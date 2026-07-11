package ai.opencode.remote.viewmodel

import ai.opencode.remote.OpenCodeApp
import ai.opencode.remote.data.models.*
import ai.opencode.remote.extractText
import ai.opencode.remote.modelFromKey
import ai.opencode.remote.modelKey
import ai.opencode.remote.sameModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DetailUiState(
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val sessionDirectory: String = "",
    val sessionStatus: String = "idle",
    val sessionUpdated: Long = 0,
    val messages: List<MessageEnvelope> = emptyList(),
    val optimisticMessages: List<MessageEnvelope> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val diffFiles: List<DiffFile> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isWaitingForReply: Boolean = false,
    val error: String? = null,
    val composerText: String = "",
    val agents: List<AgentOption> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val selectedModelKey: String? = null,
    val selectedAgentId: String = "build",
    val agentLoadError: String? = null,
    val modelLoadError: String? = null,
    val project: ProjectCurrent? = null,
    val vcs: VcsStatus? = null,
    val fileStatuses: List<FileStatusEntry> = emptyList(),
    val dashboardError: String? = null,
    val todosExpanded: Boolean = false,
    val showAiSheet: Boolean = false,
    val showDetailsSheet: Boolean = false,
    val modelQuery: String = ""
) {
    val isWorking: Boolean
        get() = isWaitingForReply || isSending || sessionStatus == "busy" || sessionStatus == "retry"

    val showTypingBubble: Boolean
        get() = sessionId != null && isWorking

    val renderedMessages: List<Pair<MessageEnvelope, String>>
        get() = (messages + optimisticMessages)
            .map { it to extractText(it) }
            .filter { it.second.isNotBlank() }

    val primaryAgents: List<AgentOption>
        get() = agents.filter { it.mode == "primary" || it.mode == "all" }

    val activeAgent: AgentOption?
        get() = primaryAgents.find { it.id == selectedAgentId }
            ?: primaryAgents.find { it.id == "build" }
            ?: primaryAgents.firstOrNull()

    val activeAgentId: String
        get() = activeAgent?.id ?: "build"

    val selectedModel: ModelSelection?
        get() = modelFromKey(selectedModelKey)

    val activeModelOption: ModelOption?
        get() {
            val sel = selectedModel
                if (sel != null) {
                    models.find { sameModel(ModelSelection(it.providerID, it.modelID, it.variant), sel) }?.let { return it }
                }
            return models.find { it.isDefault == true } ?: models.firstOrNull()
        }

    val activeModel: ModelSelection?
        get() = activeModelOption?.let { ModelSelection(it.providerID, it.modelID, it.variant) } ?: selectedModel

    val filteredModels: List<ModelOption>
        get() {
            val text = modelQuery.trim().lowercase()
            if (text.isEmpty()) return models
            return models.filter { opt ->
                listOf(opt.modelName, opt.modelID, opt.providerName, opt.providerID, opt.variant ?: "")
                    .joinToString(" ").lowercase().contains(text)
            }
        }

    val totalDiffAdditions: Int
        get() = diffFiles.sumOf { it.additions }

    val totalDiffDeletions: Int
        get() = diffFiles.sumOf { it.deletions }
}

class DetailViewModel(
    private val app: OpenCodeApp
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var loadRequestIndex = 0
    private var wasAwaitingReply = false
    private var shouldPlayCompletion = false
    private var awaitingBaseline = ""

    fun openSession(sessionId: String, title: String, directory: String, status: String, updated: Long) {
        _uiState.value = DetailUiState(
            sessionId = sessionId,
            sessionTitle = title,
            sessionDirectory = directory,
            sessionStatus = status,
            sessionUpdated = updated
        )
        loadSessionData(sessionId, directory)
        loadAgents(directory)
        loadModels(directory)
        startPolling(sessionId, directory)
    }

    fun closeSession() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.value = DetailUiState()
    }

    private fun startPolling(sessionId: String, directory: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3500)
                loadSessionData(sessionId, directory, silent = true)
                refreshSessionStatus(sessionId, directory)
            }
        }
    }

    private fun refreshSessionStatus(sessionId: String, directory: String) {
        viewModelScope.launch {
            try {
                val config = getConfig()
                val statuses = app.apiClient.listStatuses(config, directory.ifBlank { null })
                val status = statuses[sessionId]
                val prevStatus = _uiState.value.sessionStatus
                val newStatus = status?.type ?: "idle"
                val wasRunning = prevStatus == "busy" || prevStatus == "retry"
                val isNowIdle = newStatus != "busy" && newStatus != "retry"
                if (wasRunning && isNowIdle) {
                    if (shouldPlayCompletion) {
                        shouldPlayCompletion = false
                        app.playCompletionSound()
                    }
                }
                val finalWaiting = if (isNowIdle && !_uiState.value.isSending) false else _uiState.value.isWaitingForReply
                _uiState.value = _uiState.value.copy(
                    sessionStatus = newStatus,
                    isWaitingForReply = finalWaiting
                )
            } catch (e: Exception) { }
        }
    }

    private fun loadSessionData(sessionId: String, directory: String, silent: Boolean = false) {
        val requestIndex = ++loadRequestIndex
        if (!silent) _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val config = getConfig()
                val dir = directory.ifBlank { null }
                val msg = app.apiClient.loadMessages(config, sessionId, dir)
                val todo = app.apiClient.loadTodo(config, sessionId, dir)
                val diff = try { app.apiClient.loadDiff(config, sessionId, dir) } catch (e: Exception) { emptyList() }

                if (requestIndex != loadRequestIndex) return@launch

                _uiState.value = _uiState.value.copy(
                    messages = msg,
                    todos = todo,
                    diffFiles = diff,
                    isLoading = false,
                    error = null
                )

                // Check if assistant reply arrived
                checkAssistantReply(msg)

                // Load project dashboard
                loadProjectDashboard(directory)
            } catch (e: Exception) {
                if (requestIndex == loadRequestIndex) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    private fun loadProjectDashboard(directory: String) {
        viewModelScope.launch {
            try {
                val config = getConfig()
                val dir = directory.ifBlank { null }
                val project = try { app.apiClient.loadProjectCurrent(config, dir) } catch (e: Exception) { null }
                val vcs = try { app.apiClient.loadVcs(config, dir) } catch (e: Exception) { null }
                val fileStatus = try { app.apiClient.loadFileStatus(config, dir) } catch (e: Exception) { emptyList() }
                _uiState.value = _uiState.value.copy(
                    project = project,
                    vcs = vcs,
                    fileStatuses = fileStatus,
                    dashboardError = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(dashboardError = e.message)
            }
        }
    }

    private fun loadAgents(directory: String) {
        viewModelScope.launch {
            try {
                val config = getConfig()
                val dir = directory.ifBlank { null }
                val agents = app.apiClient.listAgents(config, dir)
                val savedAgent = app.preferences.agentId.first()
                val primary = agents.filter { it.mode == "primary" || it.mode == "all" }
                val nextAgent = primary.find { it.id == savedAgent } ?: primary.find { it.id == "build" } ?: primary.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    agents = agents,
                    agentLoadError = null,
                    selectedAgentId = nextAgent?.id ?: savedAgent
                )
                if (nextAgent != null) {
                    app.preferences.saveAgentId(nextAgent.id)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(agentLoadError = e.message)
            }
        }
    }

    private fun loadModels(directory: String) {
        viewModelScope.launch {
            try {
                val config = getConfig()
                val dir = directory.ifBlank { null }
                val models = app.apiClient.listModels(config, dir)
                val savedKey = app.preferences.modelKey.first()
                val savedModel = modelFromKey(savedKey)

                val modelToUse = if (savedModel != null && models.any { sameModel(ModelSelection(it.providerID, it.modelID, it.variant), savedModel) }) {
                    savedKey
                } else {
                    val fallback = models.find { it.isDefault == true } ?: models.firstOrNull()
                    fallback?.let { modelKey(ModelSelection(it.providerID, it.modelID, it.variant)) }
                }

                _uiState.value = _uiState.value.copy(
                    models = models,
                    modelLoadError = null,
                    selectedModelKey = modelToUse
                )
                if (modelToUse != null) {
                    app.preferences.saveModelKey(modelToUse)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(modelLoadError = e.message)
            }
        }
    }

    private fun checkAssistantReply(messages: List<MessageEnvelope>) {
        if (!_uiState.value.isWaitingForReply) return
        val currentSignature = messages
            .filter { it.info.role != "user" }
            .map { it to extractText(it) }
            .filter { it.second.isNotEmpty() }
            .joinToString("|") { "${it.first.info.id}:${it.second.length}" }
        if (currentSignature != awaitingBaseline) {
            _uiState.value = _uiState.value.copy(isWaitingForReply = false)
            if (shouldPlayCompletion) {
                shouldPlayCompletion = false
                app.playCompletionSound()
            }
        }
    }

    fun updateComposer(text: String) {
        _uiState.value = _uiState.value.copy(composerText = text)
    }

    fun setModelKey(key: String) {
        _uiState.value = _uiState.value.copy(selectedModelKey = key, showAiSheet = false)
        viewModelScope.launch { app.preferences.saveModelKey(key) }
    }

    fun setAgentId(id: String) {
        _uiState.value = _uiState.value.copy(selectedAgentId = id)
        viewModelScope.launch { app.preferences.saveAgentId(id) }
    }

    fun setModelQuery(query: String) {
        _uiState.value = _uiState.value.copy(modelQuery = query)
    }

    fun toggleTodos() {
        _uiState.value = _uiState.value.copy(todosExpanded = !_uiState.value.todosExpanded)
    }

    fun showAiSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAiSheet = show)
    }

    fun showDetailsSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDetailsSheet = show)
    }

    fun sendPrompt() {
        val state = _uiState.value
        val sessionId = state.sessionId ?: return
        val text = state.composerText.trim()
        if (text.isEmpty()) return

        if (text.startsWith("/")) {
            handleSlashCommand(text, sessionId)
            return
        }

        val optimisticMsg = createOptimisticUserMessage(sessionId, text)
        val currentMessages = state.messages
        val baseline = currentMessages
            .filter { it.info.role != "user" }
            .map { it to extractText(it) }
            .filter { it.second.isNotEmpty() }
            .joinToString("|") { "${it.first.info.id}:${it.second.length}" }
        awaitingBaseline = baseline
        shouldPlayCompletion = true

        _uiState.value = state.copy(
            composerText = "",
            isSending = true,
            isWaitingForReply = true,
            optimisticMessages = state.optimisticMessages + optimisticMsg,
            error = null
        )

        viewModelScope.launch {
            try {
                val config = getConfig()
                val dir = state.sessionDirectory.ifBlank { null }
                app.apiClient.sendPrompt(config, sessionId, text, dir, state.activeModel, state.activeAgentId)
                loadSessionData(sessionId, state.sessionDirectory)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    optimisticMessages = _uiState.value.optimisticMessages.filter { it.info.id != optimisticMsg.info.id }
                )
            } catch (e: Exception) {
                shouldPlayCompletion = false
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isWaitingForReply = false,
                    optimisticMessages = _uiState.value.optimisticMessages.filter { it.info.id != optimisticMsg.info.id },
                    composerText = text,
                    error = e.message
                )
            }
        }
    }

    private fun handleSlashCommand(text: String, sessionId: String) {
        val state = _uiState.value
        val normalized = text.substring(1)
        val command = normalized.split(" ").firstOrNull()?.trim() ?: ""
        val args = normalized.substring(command.length).trim()
        val localCommand = command.lowercase()

        when (localCommand) {
            "help", "commands", "skills" -> {
                _uiState.value = state.copy(composerText = "")
                // Navigate to help - handled by UI
                return
            }
            "status" -> {
                val statusText = buildString {
                    appendLine("Connection: ${state.sessionStatus}")
                    appendLine("Session: ${state.sessionTitle} (${state.sessionStatus})")
                    appendLine("Directory: ${state.sessionDirectory}")
                    appendLine("Agent: ${state.activeAgent?.name ?: state.selectedAgentId}")
                    appendLine("Model: ${state.activeModelOption?.let { "${it.providerName} / ${it.modelName}" } ?: "default"}")
                }
                val localAssistant = createLocalAssistantMessage(sessionId, statusText)
                val optimisticMsg = createOptimisticUserMessage(sessionId, text)
                _uiState.value = state.copy(
                    composerText = "",
                    optimisticMessages = state.optimisticMessages + listOf(optimisticMsg, localAssistant)
                )
                return
            }
        }

        val optimisticMsg = createOptimisticUserMessage(sessionId, text)
        val baseline = state.messages
            .filter { it.info.role != "user" }
            .map { it to extractText(it) }
            .filter { it.second.isNotEmpty() }
            .joinToString("|") { "${it.first.info.id}:${it.second.length}" }
        awaitingBaseline = baseline
        shouldPlayCompletion = true

        _uiState.value = state.copy(
            composerText = "",
            isSending = true,
            isWaitingForReply = true,
            optimisticMessages = state.optimisticMessages + optimisticMsg,
            error = null
        )

        viewModelScope.launch {
            try {
                val config = getConfig()
                val dir = state.sessionDirectory.ifBlank { null }
                app.apiClient.sendCommand(config, sessionId, command, args, dir, state.activeModel, state.activeAgentId)
                loadSessionData(sessionId, state.sessionDirectory)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    optimisticMessages = _uiState.value.optimisticMessages.filter { it.info.id != optimisticMsg.info.id }
                )
            } catch (e: Exception) {
                shouldPlayCompletion = false
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isWaitingForReply = false,
                    optimisticMessages = _uiState.value.optimisticMessages.filter { it.info.id != optimisticMsg.info.id },
                    composerText = text,
                    error = e.message
                )
            }
        }
    }

    fun abortSession() {
        val state = _uiState.value
        val sessionId = state.sessionId ?: return
        shouldPlayCompletion = false
        _uiState.value = _uiState.value.copy(isWaitingForReply = false, isSending = false)
        viewModelScope.launch {
            try {
                val config = getConfig()
                val dir = state.sessionDirectory.ifBlank { null }
                app.apiClient.abortSession(config, sessionId, dir)
                loadSessionData(sessionId, state.sessionDirectory)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun createOptimisticUserMessage(sessionId: String, text: String): MessageEnvelope {
        val now = System.currentTimeMillis()
        return MessageEnvelope(
            info = MessageInfo(
                id = "optimistic-$now",
                role = "user",
                sessionID = sessionId,
                time = MessageTime(created = now)
            ),
            parts = listOf(MessagePart(id = "optimistic-part-$now", type = "text", text = text))
        )
    }

    private fun createLocalAssistantMessage(sessionId: String, text: String): MessageEnvelope {
        val now = System.currentTimeMillis()
        return MessageEnvelope(
            info = MessageInfo(
                id = "local-assistant-$now",
                role = "assistant",
                sessionID = sessionId,
                time = MessageTime(created = now, completed = now)
            ),
            parts = listOf(MessagePart(id = "local-assistant-part-$now", type = "text", text = text))
        )
    }

    private fun getConfig(): ServerConfig {
        return runBlocking { app.preferences.serverConfig.first() }
    }
}
