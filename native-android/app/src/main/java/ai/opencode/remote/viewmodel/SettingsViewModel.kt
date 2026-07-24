package ai.opencode.remote.viewmodel

import ai.opencode.remote.I18n
import ai.opencode.remote.Language
import ai.opencode.remote.OpenCodeApp
import ai.opencode.remote.ThemePref
import ai.opencode.remote.BuildConfig
import ai.opencode.remote.data.models.UpdateInfo
import ai.opencode.remote.data.models.HealthResponse
import ai.opencode.remote.data.models.ServerConfig
import ai.opencode.remote.data.update.UpdateResult
import ai.opencode.remote.store.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val savedConfig: ServerConfig = ServerConfig(),
    val draftConfig: ServerConfig = ServerConfig(),
    val savedWorkingRootDirectory: String = "",
    val draftWorkingRootDirectory: String = "",
    val language: Language = Language.EN,
    val theme: ThemePref = ThemePref.SYSTEM,
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val connectedVersion: String = "",
    val notice: SettingsNotice? = null,
    val lastTestedConfigKey: String? = null,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val updateInfo: UpdateInfo? = null
) {
    val hasDraftChanges: Boolean
        get() = configKey(draftConfig) != configKey(savedConfig) ||
            draftWorkingRootDirectory != savedWorkingRootDirectory

    val canTestDraft: Boolean
        get() = draftConfig.host.isNotBlank() && draftConfig.port > 0 && draftConfig.username.isNotBlank()

    val testAlreadyPassed: Boolean
        get() = lastTestedConfigKey == configKey(draftConfig)

    val hasConfiguredServer: Boolean
        get() = savedConfig.host.isNotBlank() && savedConfig.port > 0

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE
}

/** State of the update check / download flow. */
enum class UpdateStatus {
    Idle, Checking, Available, Downloading, ReadyToInstall, Error, Skipped
}

data class SettingsNotice(
    val type: NoticeType,
    val text: String
)

enum class NoticeType { INFO, SUCCESS, ERROR }

private fun configKey(config: ServerConfig): String {
    return "${config.host.trim()}:${config.port}:${config.username.trim()}:${config.password}"
}

class SettingsViewModel(
    private val app: OpenCodeApp
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val config = app.preferences.serverConfig.first()
            val lang = app.preferences.language.first()
            val theme = app.preferences.theme.first()
            val workingRoot = app.preferences.workingRootDirectory.first()
            _uiState.value = SettingsUiState(
                savedConfig = config,
                draftConfig = config,
                savedWorkingRootDirectory = workingRoot,
                draftWorkingRootDirectory = workingRoot,
                language = Language.fromCode(lang),
                theme = ThemePref.fromKey(theme)
            )
            I18n.setLanguage(Language.fromCode(lang))
        }
    }

    fun updateDraftHost(host: String) {
        _uiState.value = _uiState.value.copy(draftConfig = _uiState.value.draftConfig.copy(host = host))
    }

    fun updateDraftPort(port: Int) {
        _uiState.value = _uiState.value.copy(draftConfig = _uiState.value.draftConfig.copy(port = port))
    }

    fun updateDraftUsername(username: String) {
        _uiState.value = _uiState.value.copy(draftConfig = _uiState.value.draftConfig.copy(username = username))
    }

    fun updateDraftPassword(password: String) {
        _uiState.value = _uiState.value.copy(draftConfig = _uiState.value.draftConfig.copy(password = password))
    }

    fun updateDraftWorkingRootDirectory(dir: String) {
        _uiState.value = _uiState.value.copy(draftWorkingRootDirectory = dir)
    }

    fun updateLanguage(lang: Language) {
        _uiState.value = _uiState.value.copy(language = lang)
        I18n.setLanguage(lang)
        viewModelScope.launch {
            app.preferences.saveLanguage(lang.code)
            app.updateLanguage(lang)
        }
    }

    fun updateTheme(theme: ThemePref) {
        _uiState.value = _uiState.value.copy(theme = theme)
        viewModelScope.launch {
            app.preferences.saveTheme(theme.storageKey)
            app.updateThemePreference(theme)
        }
    }

    fun saveConfig() {
        val draft = _uiState.value.draftConfig
        val workingRootDraft = _uiState.value.draftWorkingRootDirectory
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            app.preferences.saveServerConfig(draft)
            app.preferences.saveWorkingRootDirectory(workingRootDraft)
            _uiState.value = _uiState.value.copy(
                savedConfig = draft,
                savedWorkingRootDirectory = workingRootDraft,
                isSaving = false,
                notice = SettingsNotice(NoticeType.SUCCESS, "settings_saved")
            )
        }
    }

    fun testConnection() {
        val draft = _uiState.value.draftConfig
        if (!draft.host.isNotBlank() || draft.port <= 0 || !draft.username.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                notice = SettingsNotice(NoticeType.ERROR, "settings_testNeedsFields")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, notice = SettingsNotice(NoticeType.INFO, "settings_testingConnection"))
            try {
                val health = app.apiClient.health(draft)
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    connectedVersion = health.version,
                    lastTestedConfigKey = configKey(draft),
                    notice = SettingsNotice(NoticeType.SUCCESS, "settings_testedNotSaved:${health.version}")
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    notice = SettingsNotice(NoticeType.ERROR, "settings_connectionFailed:${e.message}")
                )
            }
        }
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    // --- Update actions ---

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Checking)
            val updateUrl = app.preferences.updateUrl.first()
            app.preferences.saveLastUpdateCheck(System.currentTimeMillis())
            val result = app.updateManager.checkForUpdate(updateUrl)
            when (result) {
                is UpdateResult.Available -> {
                    val skipped = app.preferences.skippedVersion.first()
                    if (result.info.versionCode == skipped) {
                        _uiState.value = _uiState.value.copy(
                            updateStatus = UpdateStatus.Skipped,
                            updateInfo = result.info
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            updateStatus = UpdateStatus.Available,
                            updateInfo = result.info
                        )
                    }
                }
                is UpdateResult.NotAvailable -> {
                    _uiState.value = _uiState.value.copy(
                        updateStatus = UpdateStatus.Idle,
                        updateInfo = null
                    )
                }
                is UpdateResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        updateStatus = UpdateStatus.Error,
                        updateInfo = null,
                        notice = SettingsNotice(NoticeType.ERROR, "Update check failed: ${result.message}")
                    )
                }
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Downloading)
        app.updateManager.downloadUpdate(info)
    }

    fun skipVersion(versionCode: Int) {
        viewModelScope.launch {
            app.preferences.saveSkippedVersion(versionCode)
            _uiState.value = _uiState.value.copy(
                updateStatus = UpdateStatus.Idle,
                updateInfo = null
            )
        }
    }

    fun resetUpdateStatus() {
        _uiState.value = _uiState.value.copy(
            updateStatus = UpdateStatus.Idle,
            updateInfo = null
        )
    }
}
