package ai.opencode.remote.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ai.opencode.remote.ThemePref

private val OpenCodeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = SurfaceLight,
    primaryContainer = PrimarySoftLight,
    onPrimaryContainer = PrimaryStrongLight,
    secondary = MutedStrongLight,
    onSecondary = SurfaceLight,
    secondaryContainer = SurfaceSubtleLight,
    onSecondaryContainer = TextLight,
    tertiary = SuccessLight,
    onTertiary = SurfaceLight,
    tertiaryContainer = SuccessSoftLight,
    onTertiaryContainer = SuccessLight,
    error = DangerLight,
    onError = SurfaceLight,
    errorContainer = DangerSoftLight,
    onErrorContainer = DangerLight,
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceSubtleLight,
    onSurfaceVariant = MutedLight,
    surfaceTint = PrimaryLight,
    outline = BorderLight,
    outlineVariant = SurfaceStrongLight,
    scrim = Color(0x73142033)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = BgDark,
    primaryContainer = PrimarySoftDark,
    onPrimaryContainer = PrimaryStrongDark,
    secondary = MutedStrongDark,
    onSecondary = BgDark,
    secondaryContainer = SurfaceSubtleDark,
    onSecondaryContainer = TextDark,
    tertiary = SuccessDark,
    onTertiary = BgDark,
    tertiaryContainer = SuccessSoftDark,
    onTertiaryContainer = SuccessDark,
    error = DangerDark,
    onError = BgDark,
    errorContainer = DangerSoftDark,
    onErrorContainer = DangerDark,
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = SurfaceSubtleDark,
    onSurfaceVariant = MutedDark,
    surfaceTint = PrimaryDark,
    outline = BorderDark,
    outlineVariant = SurfaceStrongDark,
    scrim = Color(0xA8000000)
)

@Composable
fun OpenCodeTheme(
    themePreference: ThemePref = ThemePref.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePreference) {
        ThemePref.SYSTEM -> systemDark
        ThemePref.LIGHT -> false
        ThemePref.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenCodeTypography,
        shapes = OpenCodeShapes,
        content = content
    )
}
