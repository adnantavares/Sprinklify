package com.challenge.sprinklify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CartoonColorScheme = lightColorScheme(
    primary = SkyBlue,
    secondary = SunnyYellow,
    tertiary = DarkNavy,
    background = Cream,
    surface = Cream,
    onPrimary = White,
    onSecondary = DarkNavy,
    onTertiary = White,
    onBackground = DarkNavy,
    onSurface = DarkNavy,
)

@Composable
fun CartoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CartoonColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
