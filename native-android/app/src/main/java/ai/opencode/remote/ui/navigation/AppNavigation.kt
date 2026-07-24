package ai.opencode.remote.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.opencode.remote.OpenCodeApp
import ai.opencode.remote.data.models.ModelSelection
import ai.opencode.remote.data.models.ServerConfig
import ai.opencode.remote.ui.screens.DetailScreen
import ai.opencode.remote.ui.screens.DocumentViewerScreen
import ai.opencode.remote.ui.screens.HelpScreen
import ai.opencode.remote.ui.screens.SessionsScreen
import ai.opencode.remote.ui.screens.SettingsScreen
import ai.opencode.remote.viewmodel.DetailViewModel
import ai.opencode.remote.viewmodel.SessionsViewModel
import ai.opencode.remote.viewmodel.SettingsViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    Sessions("Sessions", Icons.Filled.ViewList),
    Detail("Detail", Icons.Filled.Chat),
    Settings("Settings", Icons.Filled.Settings),
    Help("Help", Icons.Filled.Help)
}

@Composable
fun AppNavigation() {
    val app = OpenCodeApp.get()
    var selectedTab by remember { mutableStateOf(Tab.Sessions) }
    var selectedSession by remember {
        mutableStateOf<Triple<String, String, String>?>(null)
    }
    var selectedSessionStatus by remember { mutableStateOf("idle") }
    var selectedSessionUpdated by remember { mutableStateOf(0L) }

    // Document viewer state
    var docViewerFile by remember { mutableStateOf<String?>(null) }
    var docViewerSessionId by remember { mutableStateOf<String?>(null) }
    var docViewerContent by remember { mutableStateOf<String?>(null) }
    var docViewerLoading by remember { mutableStateOf(false) }
    var docViewerError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val settingsViewModel: SettingsViewModel = viewModel(factory = SimpleViewModelFactory { SettingsViewModel(app) })
    val sessionsViewModel: SessionsViewModel = viewModel(factory = SimpleViewModelFactory { SessionsViewModel(app) })
    val detailViewModel: DetailViewModel = viewModel(factory = SimpleViewModelFactory { DetailViewModel(app) })

    val settingsState by settingsViewModel.uiState.collectAsState()
    val sessionsState by sessionsViewModel.uiState.collectAsState()
    val detailState by detailViewModel.uiState.collectAsState()

    val hasServer = settingsState.hasConfiguredServer

    // Apply saved config to API client on startup
    LaunchedEffect(Unit) {
        if (hasServer) {
            app.apiClient.configure(
                settingsState.savedConfig.host,
                settingsState.savedConfig.port,
                settingsState.savedConfig.username,
                settingsState.savedConfig.password
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasServer, selectedTab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (hasServer && selectedTab == Tab.Sessions) {
                    sessionsViewModel.startPolling()
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                sessionsViewModel.stopPolling()
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (hasServer && selectedTab == Tab.Sessions) {
                sessionsViewModel.startPolling()
            } else {
                sessionsViewModel.stopPolling()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionsViewModel.stopPolling()
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedTab == Tab.Sessions || selectedTab == Tab.Detail) {
                SessionsScreen(
                    state = sessionsState,
                    onSearchQueryChange = sessionsViewModel::updateSearchQuery,
                    onRefresh = sessionsViewModel::refreshSessionsManually,
                    onNewSession = sessionsViewModel::showNewSessionPicker,
                    onSessionClick = { session ->
                        selectedSession = Triple(session.id, session.title, session.directory)
                        selectedSessionStatus = session.status
                        selectedSessionUpdated = session.updated
                        detailViewModel.openSession(
                            session.id, session.title, session.directory,
                            session.status, session.updated
                        )
                        selectedTab = Tab.Detail
                    },
                    onDeleteSession = sessionsViewModel::deleteSession,
                    onToggleProjectCollapsed = sessionsViewModel::toggleProjectCollapsed,
                    onConfirmDelete = sessionsViewModel::confirmDeleteSession,
                    onCancelDelete = sessionsViewModel::cancelDeleteSession,
                    onPickerBrowse = sessionsViewModel::browseDirectory,
                    onPickerSelectDir = { dir ->
                        sessionsViewModel.setNewSessionDirectory(dir)
                    },
                    onPickerCreate = { dir ->
                        sessionsViewModel.createSession(dir) { created ->
                            selectedSession = Triple(created.id, created.title, created.directory)
                            selectedSessionStatus = "idle"
                            selectedSessionUpdated = created.time.updated
                            detailViewModel.openSession(
                                created.id, created.title, created.directory,
                                "idle", created.time.updated
                            )
                            selectedTab = Tab.Detail
                        }
                    },
                    onPickerDismiss = sessionsViewModel::hideNewSessionPicker,
                    onCommandFilterChange = sessionsViewModel::setCommandFilter,
                    onErrorDismiss = sessionsViewModel::clearError,
                    onHelpClick = { selectedTab = Tab.Help },
                    onSettingsClick = { selectedTab = Tab.Settings },
                    onSwipeLeft = {
                        val session = sessionsState.sessions.firstOrNull()
                        if (session != null) {
                            selectedSession = Triple(session.id, session.title, session.directory)
                            selectedSessionStatus = session.status
                            selectedSessionUpdated = session.updated
                            detailViewModel.openSession(
                                session.id, session.title, session.directory,
                                session.status, session.updated
                            )
                            selectedTab = Tab.Detail
                        }
                    }
                )
            }

            if (selectedTab == Tab.Detail) {
                val sessionInfo = selectedSession
                if (sessionInfo != null) {
                    DetailScreen(
                        state = detailState,
                        onBack = {
                            detailViewModel.closeSession()
                            selectedTab = Tab.Sessions
                        },
                        onComposerChange = detailViewModel::updateComposer,
                        onSend = detailViewModel::sendPrompt,
                        onAbort = detailViewModel::abortSession,
                        onModelKeyChange = detailViewModel::setModelKey,
                        onAgentChange = detailViewModel::setAgentId,
                        onModelQueryChange = detailViewModel::setModelQuery,
                        onToggleTodos = detailViewModel::toggleTodos,
                        onShowAiSheet = { detailViewModel.showAiSheet(true) },
                        onHideAiSheet = { detailViewModel.showAiSheet(false) },
                        onShowDetailsSheet = { detailViewModel.showDetailsSheet(true) },
                        onHideDetailsSheet = { detailViewModel.showDetailsSheet(false) },
                        onCommandOptionClick = detailViewModel::selectCommandOption,
                        onErrorDismiss = detailViewModel::clearError,
                        onOpenDocument = { filePath, sessionId ->
                            scope.launch {
                                docViewerFile = filePath
                                docViewerSessionId = sessionId
                                docViewerContent = null
                                docViewerError = null
                                docViewerLoading = true
                                try {
                                    val config = app.preferences.serverConfig.first()
                                    val content = app.apiClient.readFileContent(
                                        config, filePath,
                                        detailState.sessionDirectory.ifBlank { null },
                                        sessionId
                                    )
                                    docViewerContent = content
                                    docViewerLoading = false
                                } catch (e: Exception) {
                                    docViewerError = e.message ?: "Failed to load file"
                                    docViewerLoading = false
                                }
                            }
                        }
                    )
                }
            }

            if (selectedTab == Tab.Settings) {
                SettingsScreen(
                    state = settingsState,
                    onHostChange = settingsViewModel::updateDraftHost,
                    onPortChange = settingsViewModel::updateDraftPort,
                    onUsernameChange = settingsViewModel::updateDraftUsername,
                    onPasswordChange = settingsViewModel::updateDraftPassword,
                    onWorkingRootChange = settingsViewModel::updateDraftWorkingRootDirectory,
                    onLanguageChange = settingsViewModel::updateLanguage,
                    onThemeChange = settingsViewModel::updateTheme,
                    onSave = settingsViewModel::saveConfig,
                    onTest = settingsViewModel::testConnection,
                    onNoticeDismiss = settingsViewModel::clearNotice,
                    onBack = { selectedTab = Tab.Sessions },
                    onCheckForUpdate = settingsViewModel::checkForUpdate,
                    onDownloadUpdate = {
                        settingsState.updateInfo?.let { settingsViewModel.startDownload(it) }
                    },
                    onSkipVersion = { vc -> settingsViewModel.skipVersion(vc) },
                    onResetUpdate = settingsViewModel::resetUpdateStatus
                )
            }

            if (selectedTab == Tab.Help) {
                HelpScreen(
                    state = sessionsState,
                    onCommandFilterChange = sessionsViewModel::setCommandFilter,
                    onCommandSearchChange = sessionsViewModel::setCommandSearch,
                    onBack = { selectedTab = Tab.Sessions }
                )
            }

            // Document viewer overlay
            docViewerFile?.let { filePath ->
                DocumentViewerScreen(
                    filePath = filePath,
                    fileContent = docViewerContent ?: "",
                    isLoading = docViewerLoading,
                    error = docViewerError,
                    onBack = {
                        docViewerFile = null
                        docViewerSessionId = null
                        docViewerContent = null
                        docViewerError = null
                        docViewerLoading = false
                    },
                    onRetry = {
                        scope.launch {
                            docViewerContent = null
                            docViewerError = null
                            docViewerLoading = true
                            try {
                                val config = app.preferences.serverConfig.first()
                                val content = app.apiClient.readFileContent(
                                    config,
                                    docViewerFile ?: return@launch,
                                    detailState.sessionDirectory.ifBlank { null },
                                    docViewerSessionId
                                )
                                docViewerContent = content
                                docViewerLoading = false
                            } catch (e: Exception) {
                                docViewerError = e.message ?: "Failed to load file"
                                docViewerLoading = false
                            }
                        }
                    }
                )
            }
        }
    }
}

class SimpleViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = creator() as V
}
