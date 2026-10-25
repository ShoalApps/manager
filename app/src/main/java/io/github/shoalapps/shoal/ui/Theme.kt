@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.shoal.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.shoalapps.shoal.data.Settings

// Fallback palette: Shoal's colour, generated with Material's colour utilities.
// Used on Android 11 and below, or with dynamic colour turned off.
private val Light = lightColorScheme(
    primary = Color(0xFF006B5E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9FF2E2), onPrimaryContainer = Color(0xFF005047),
    secondary = Color(0xFF4A635E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCAE6DE), onSecondaryContainer = Color(0xFF334B46),
    tertiary = Color(0xFF5B7890), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF5B7890), onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFF4FBF8), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF4FBF8), onSurface = Color(0xFF171D1B), onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainerLow = Color(0xFFEFF5F2), surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE7), surfaceContainerHighest = Color(0xFFDDE4E1),
    outline = Color(0xFF6D7774), outlineVariant = Color(0xFFBEC9C5), inversePrimary = Color(0xFF83D5C6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF83D5C6), onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047), onPrimaryContainer = Color(0xFF9FF2E2),
    secondary = Color(0xFFB1CCC5), onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF354E49), onSecondaryContainer = Color(0xFFCDE8E1),
    tertiary = Color(0xFFACCAE5), onTertiary = Color(0xFF133348),
    tertiaryContainer = Color(0xFF7794AD), onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF0E1513), onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513), onSurface = Color(0xFFDDE4E1), onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLow = Color(0xFF171D1B), surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B2A), surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4946), inversePrimary = Color(0xFF006B5E),
)

@Composable
fun ManagerTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
