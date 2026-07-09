package ai.opencode.remote.store

import ai.opencode.remote.data.models.ServerConfig
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opencode_remote_prefs")

class Preferences(private val context: Context) {

    companion object {
        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_PORT = intPreferencesKey("server_port")
        private val KEY_USERNAME = stringPreferencesKey("server_username")
        private val KEY_PASSWORD = stringPreferencesKey("server_password")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_MODEL = stringPreferencesKey("selected_model_key")
        private val KEY_AGENT = stringPreferencesKey("selected_agent_id")
        private val KEY_NEW_SESSION_DIR = stringPreferencesKey("new_session_directory")
    }

    val serverConfig: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            host = prefs[KEY_HOST] ?: "",
            port = prefs[KEY_PORT] ?: 4096,
            username = prefs[KEY_USERNAME] ?: "opencode",
            password = prefs[KEY_PASSWORD] ?: ""
        )
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = config.host
            prefs[KEY_PORT] = config.port
            prefs[KEY_USERNAME] = config.username
            prefs[KEY_PASSWORD] = config.password
        }
    }

    val language: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "en" }

    suspend fun saveLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    val theme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "system" }

    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    val modelKey: Flow<String?> = context.dataStore.data.map { it[KEY_MODEL] }

    suspend fun saveModelKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key != null) prefs[KEY_MODEL] = key
            else prefs.remove(KEY_MODEL)
        }
    }

    val agentId: Flow<String> = context.dataStore.data.map { it[KEY_AGENT] ?: "build" }

    suspend fun saveAgentId(id: String) {
        context.dataStore.edit { it[KEY_AGENT] = id }
    }

    val newSessionDirectory: Flow<String> = context.dataStore.data.map { it[KEY_NEW_SESSION_DIR] ?: "" }

    suspend fun saveNewSessionDirectory(dir: String) {
        context.dataStore.edit { it[KEY_NEW_SESSION_DIR] = dir }
    }
}
