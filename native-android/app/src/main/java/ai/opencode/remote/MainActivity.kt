package ai.opencode.remote

import ai.opencode.remote.ui.navigation.AppNavigation
import ai.opencode.remote.ui.theme.OpenCodeTheme
import ai.opencode.remote.ThemePref
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OpenCodeApp

        // Load persisted settings on startup
        lifecycleScope.launch {
            val lang = app.preferences.language.first()
            val theme = app.preferences.theme.first()
            app.updateLanguage(Language.fromCode(lang))
            app.updateThemePreference(ThemePref.fromKey(theme))
            I18n.setLanguage(app.language)
        }

        setContent {
            val themePref by app.preferences.theme.collectAsState(initial = "system")
            OpenCodeTheme(
                themePreference = ThemePref.fromKey(themePref)
            ) {
                AppNavigation()
            }
        }
    }
}
