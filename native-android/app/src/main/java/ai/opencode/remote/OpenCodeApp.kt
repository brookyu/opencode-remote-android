package ai.opencode.remote

import ai.opencode.remote.data.api.ApiClient
import ai.opencode.remote.data.update.UpdateManager
import ai.opencode.remote.store.Preferences
import android.app.Application
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class OpenCodeApp : Application() {

    lateinit var preferences: Preferences
        private set

    val apiClient: ApiClient = ApiClient()

    lateinit var updateManager: UpdateManager
        private set

    var soundPool: SoundPool? = null
    var completionSoundId: Int = 0

    var language by mutableStateOf(Language.EN)
        private set

    var themePreference by mutableStateOf(ThemePref.SYSTEM)
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = Preferences(this)
        updateManager = UpdateManager(this)

        // Initialize SoundPool for completion sound
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load completion sound from raw resource
        completionSoundId = try {
            soundPool?.load(this, R.raw.completion, 1) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun playCompletionSound() {
        if (completionSoundId != 0) {
            soundPool?.play(completionSoundId, 0.6f, 0.6f, 1, 0, 1f)
        }
    }

    fun updateLanguage(lang: Language) {
        language = lang
        I18n.setLanguage(lang)
    }

    fun updateThemePreference(theme: ThemePref) {
        themePreference = theme
    }

    companion object {
        @Volatile
        private var instance: OpenCodeApp? = null

        fun get(): OpenCodeApp = instance!!
    }
}
