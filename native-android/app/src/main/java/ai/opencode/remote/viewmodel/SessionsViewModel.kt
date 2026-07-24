package ai.opencode.remote.viewmodel

import ai.opencode.remote.OpenCodeApp
import ai.opencode.remote.data.models.*
import ai.opencode.remote.formatTime
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

data class SessionView(
    val id: String,
    val title: String,
    val directory: String,
    val updated: Long,
    val status: String = "idle",
    val files: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val model: ModelSelection? = null
)

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, RECONNECTING, OFFLINE }

data class SessionsUiState(
    val sessions: List<SessionView> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val connectionMessage: String = "",
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val showNewSessionPicker: Boolean = false,
    val pickerPath: String = "",
    val pickerItems: List<FileEntry> = emptyList(),
    val pickerLoading: Boolean = false,
    val pickerError: String? = null,
    val newSessionDirectory: String = "",
    val sessionToDelete: SessionView? = null,
    val commands: List<CommandInfo> = emptyList(),
    val commandFilter: String = "all",
    val commandSearch: String = "",
    val collapsedProjects: Set<String> = emptySet()
) {
    val filteredSessions: List<SessionView>
        get() {
            val text = searchQuery.trim().lowercase()
            if (text.isEmpty()) return sessions
            return sessions.filter {
                it.title.lowercase().contains(text) || it.directory.lowercase().contains(text)
            }
        }

    val activeCount: Int
        get() = sessions.count { it.status == "busy" || it.status == "retry" }

    val changedCount: Int
        get() = sessions.count { it.files > 0 || it.additions > 0 || it.deletions > 0 }

    val displayedCommands: List<CommandInfo>
        get() = if (commandFilter == "skill") commands.filter { it.source == "skill" } else commands

    val filteredCommands: List<CommandInfo>
        get() {
            val text = commandSearch.trim().lowercase()
            val base = displayedCommands
            return if (text.isEmpty()) base else base.filter {
                it.name.lowercase().contains(text) || (it.description?.lowercase()?.contains(text) == true)
            }
        }
}

class SessionsViewModel(
    private val app: OpenCodeApp
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var failureCount = 0
    private var initialLoad = true
    private val activityTimeCache = ConcurrentHashMap<String, Pair<Long, Long>>()

    fun startPolling() {
        pollingJob?.cancel()
        failureCount = 0
        initialLoad = true
        refreshSessions(silent = true, force = false)
        loadCommands()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3500)
                refreshSessions(silent = true, force = true)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleProjectCollapsed(directory: String) {
        val current = _uiState.value.collapsedProjects
        val next = if (current.contains(directory)) current - directory else current + directory
        _uiState.value = _uiState.value.copy(collapsedProjects = next)
    }

    fun setCommandFilter(filter: String) {
        _uiState.value = _uiState.value.copy(commandFilter = filter)
    }

    fun setCommandSearch(search: String) {
        _uiState.value = _uiState.value.copy(commandSearch = search)
    }

    fun showNewSessionPicker() {
        _uiState.value = _uiState.value.copy(showNewSessionPicker = true, pickerError = null)
        viewModelScope.launch {
            try {
                val dir = _uiState.value.newSessionDirectory.ifBlank { null }
                val workingRoot = app.preferences.workingRootDirectory.first().ifBlank { null }
                val pathInfo = app.apiClient.loadPath(getConfig(), dir ?: workingRoot)
                browseDirectory(dir ?: workingRoot ?: pathInfo.home)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(pickerError = e.message)
            }
        }
    }

    fun hideNewSessionPicker() {
        _uiState.value = _uiState.value.copy(showNewSessionPicker = false)
    }

    fun browseDirectory(path: String) {
        _uiState.value = _uiState.value.copy(pickerLoading = true, pickerError = null)
        viewModelScope.launch {
            try {
                val items = app.apiClient.listFiles(getConfig(), "", path)
                _uiState.value = _uiState.value.copy(
                    pickerPath = path,
                    pickerItems = items.filter { it.type == "directory" && !it.name.startsWith(".") }.sortedBy { it.name },
                    pickerLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(pickerError = e.message, pickerLoading = false, pickerItems = emptyList())
            }
        }
    }

    fun setNewSessionDirectory(dir: String) {
        _uiState.value = _uiState.value.copy(newSessionDirectory = dir)
        viewModelScope.launch { app.preferences.saveNewSessionDirectory(dir) }
    }

    fun createSession(directory: String?, onSuccess: ((Session) -> Unit)? = null) {
        val dir = directory?.ifBlank { null }
        _uiState.value = _uiState.value.copy(isCreating = true, pickerError = null)
        viewModelScope.launch {
            try {
                if (dir != null) {
                    val pathInfo = app.apiClient.loadPath(getConfig(), dir)
                    if (pathInfo.worktree == "/") {
                        _uiState.value = _uiState.value.copy(
                            isCreating = false,
                            pickerError = "Not a valid OpenCode project folder"
                        )
                        return@launch
                    }
                }
                val created = app.apiClient.createSession(getConfig(), "Mobile session", null, dir)
                if (dir != null) setNewSessionDirectory(dir)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    showNewSessionPicker = false
                )
                refreshSessions(silent = false, force = true)
                onSuccess?.invoke(created)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, pickerError = e.message)
            }
        }
    }

    fun deleteSession(session: SessionView) {
        _uiState.value = _uiState.value.copy(sessionToDelete = session)
    }

    fun confirmDeleteSession() {
        val session = _uiState.value.sessionToDelete ?: return
        viewModelScope.launch {
            try {
                app.apiClient.deleteSession(getConfig(), session.id, session.directory.ifBlank { null })
                _uiState.value = _uiState.value.copy(sessionToDelete = null)
                refreshSessions(silent = true, force = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, sessionToDelete = null)
            }
        }
    }

    fun cancelDeleteSession() {
        _uiState.value = _uiState.value.copy(sessionToDelete = null)
    }

    fun refreshSessionsManually() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        refreshSessions(silent = false, force = true)
    }

    private fun refreshSessions(silent: Boolean, force: Boolean = false) {
        viewModelScope.launch {
            val config = getConfig()
            if (config.host.isBlank() || config.port <= 0) return@launch

            if (!force) {
                val cache = cachedSessionsList
                val timestamp = lastLoadedTimestamp
                if (cache != null && System.currentTimeMillis() - timestamp < 2 * 60 * 60 * 1000) {
                    _uiState.value = _uiState.value.copy(
                        sessions = cache,
                        connectionState = ConnectionState.CONNECTED,
                        error = null,
                        isRefreshing = false
                    )
                    initialLoad = false
                    return@launch
                }
            }

            if (!silent) {
                _uiState.value = _uiState.value.copy(
                    error = null,
                    connectionState = if (_uiState.value.sessions.isEmpty()) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
                )
            } else if (initialLoad && _uiState.value.sessions.isEmpty()) {
                _uiState.value = _uiState.value.copy(connectionState = ConnectionState.CONNECTING)
            }

            try {
                val items = try {
                    app.apiClient.listGlobalSessions(config)
                } catch (e: Exception) {
                    app.apiClient.listSessions(config)
                }

                val directories = items.map { it.directory }.filter { it.isNotBlank() }.distinct()
                val sessionLists = directories.map { dir ->
                    try { app.apiClient.listSessions(config, dir) } catch (e: Exception) { emptyList() }
                }
                val statusMaps = directories.map { dir ->
                    try { app.apiClient.listStatuses(config, dir) } catch (e: Exception) { emptyMap() }
                }

                val scopedSessions = mutableMapOf<String, Session>()
                sessionLists.flatten().forEach { scopedSessions[it.id] = it }
                val statuses = mutableMapOf<String, SessionStatus>()
                statusMaps.forEach { statuses.putAll(it) }

                val hydrated = items.map { session ->
                    val scoped = scopedSessions[session.id]
                    if (scoped != null) session.copy(
                        title = scoped.title.ifBlank { session.title },
                        directory = scoped.directory.ifBlank { session.directory },
                        time = scoped.time,
                        summary = scoped.summary ?: session.summary
                    ) else session
                }

                val activityTimes = loadActivityTimes(hydrated, config)
                val views = hydrated.map { session ->
                    val status = statuses[session.id]
                    val activityTime = activityTimes[session.id] ?: session.time.updated
                    SessionView(
                        id = session.id,
                        title = session.title,
                        directory = session.directory,
                        updated = activityTime,
                        status = status?.type ?: "idle",
                        files = session.summary?.files ?: 0,
                        additions = session.summary?.additions ?: 0,
                        deletions = session.summary?.deletions ?: 0,
                        model = session.model?.let { ModelSelection(it.providerID, it.id, it.variant) }
                    )
                }.sortedByDescending { it.updated }

                failureCount = 0
                initialLoad = false
                cachedSessionsList = views
                lastLoadedTimestamp = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    sessions = views,
                    connectionState = ConnectionState.CONNECTED,
                    error = null,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                if (!silent) {
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.OFFLINE,
                        error = e.message,
                        isRefreshing = false
                    )
                } else {
                    failureCount++
                    if (failureCount == 1) {
                        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.RECONNECTING, isRefreshing = false)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            connectionState = ConnectionState.OFFLINE,
                            isRefreshing = false,
                            error = if (failureCount >= 3) e.message else _uiState.value.error
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadActivityTimes(items: List<Session>, config: ServerConfig): Map<String, Long> {
        return coroutineScope {
            items.map { session ->
                async {
                    val cached = activityTimeCache[session.id]
                    if (cached != null && cached.first == session.time.updated) {
                        session.id to cached.second
                    } else {
                        try {
                            val latest = app.apiClient.loadLatestMessage(config, session.id, session.directory.ifBlank { null })
                            val activityTime = if (latest.isNotEmpty()) latest.maxOf { maxOf(it.info.time.created, it.info.time.completed ?: 0) } else session.time.updated
                            activityTimeCache[session.id] = session.time.updated to activityTime
                            session.id to activityTime
                        } catch (e: Exception) {
                            session.id to session.time.updated
                        }
                    }
                }
            }.awaitAll().toMap()
        }
    }

    private fun loadCommands() {
        viewModelScope.launch {
            try {
                val config = getConfig()
                if (config.host.isNotBlank() && config.port > 0) {
                    val cmds = app.apiClient.listCommands(config)
                    _uiState.value = _uiState.value.copy(commands = cmds)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(commands = emptyList())
            }
        }
    }

    private fun getConfig(): ServerConfig {
        return runBlocking { app.preferences.serverConfig.first() }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        private var cachedSessionsList: List<SessionView>? = null
        private var lastLoadedTimestamp: Long = 0L
    }
}
