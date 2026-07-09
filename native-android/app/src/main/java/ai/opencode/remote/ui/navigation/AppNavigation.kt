package ai.opencode.remote.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.opencode.remote.OpenCodeApp
import ai.opencode.remote.data.models.ModelSelection
import ai.opencode.remote.ui.screens.DetailScreen
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

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

    LaunchedEffect(hasServer) {
        if (hasServer) {
            sessionsViewModel.startPolling()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == Tab.Sessions,
                    onClick = { selectedTab = Tab.Sessions },
                    enabled = hasServer,
                    icon = { Icon(Tab.Sessions.icon, contentDescription = Tab.Sessions.label) },
                    label = { Text(Tab.Sessions.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.Detail,
                    onClick = { selectedTab = Tab.Detail },
                    enabled = selectedSession != null,
                    icon = { Icon(Tab.Detail.icon, contentDescription = Tab.Detail.label) },
                    label = { Text(Tab.Detail.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.Settings,
                    onClick = { selectedTab = Tab.Settings },
                    icon = { Icon(Tab.Settings.icon, contentDescription = Tab.Settings.label) },
                    label = { Text(Tab.Settings.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.Help,
                    onClick = { selectedTab = Tab.Help },
                    enabled = hasServer,
                    icon = { Icon(Tab.Help.icon, contentDescription = Tab.Help.label) },
                    label = { Text(Tab.Help.label) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                Tab.Sessions -> SessionsScreen(
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
                    onConfirmDelete = sessionsViewModel::confirmDeleteSession,
                    onCancelDelete = sessionsViewModel::cancelDeleteSession,
                    onPickerBrowse = sessionsViewModel::browseDirectory,
                    onPickerSelectDir = { dir ->
                        sessionsViewModel.setNewSessionDirectory(dir)
                    },
                    onPickerCreate = { dir -> sessionsViewModel.createSession(dir) },
                    onPickerDismiss = sessionsViewModel::hideNewSessionPicker,
                    onCommandFilterChange = sessionsViewModel::setCommandFilter,
                    onErrorDismiss = sessionsViewModel::clearError
                )

                Tab.Detail -> {
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
                            onErrorDismiss = detailViewModel::clearError
                        )
                    }
                }

                Tab.Settings -> SettingsScreen(
                    state = settingsState,
                    onHostChange = settingsViewModel::updateDraftHost,
                    onPortChange = settingsViewModel::updateDraftPort,
                    onUsernameChange = settingsViewModel::updateDraftUsername,
                    onPasswordChange = settingsViewModel::updateDraftPassword,
                    onLanguageChange = settingsViewModel::updateLanguage,
                    onThemeChange = settingsViewModel::updateTheme,
                    onSave = settingsViewModel::saveConfig,
                    onTest = settingsViewModel::testConnection,
                    onNoticeDismiss = settingsViewModel::clearNotice
                )

                Tab.Help -> HelpScreen(
                    state = sessionsState,
                    onCommandFilterChange = sessionsViewModel::setCommandFilter,
                    onCommandSearchChange = sessionsViewModel::setCommandSearch
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
