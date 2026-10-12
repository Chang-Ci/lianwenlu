package com.lianwenqi.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo40,
    secondary = IndigoGrey40,
    tertiary = Teal40,
    background = PaperLight,
    surface = PaperLight
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    secondary = IndigoGrey80,
    tertiary = Teal80,
    background = PaperDark,
    surface = PaperDark
)

@Suppress("DEPRECATION")
@Composable
fun LianWenQiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // targetSdk 35 起系统在 Android 15+ 强制边到边，此调用在 API 35+ 已是空操作；
            // 保留它仅为兼容 API 26~34 上的状态栏着色。
            window.statusBarColor = colors.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}