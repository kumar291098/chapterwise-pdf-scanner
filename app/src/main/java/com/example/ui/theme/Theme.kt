package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryIndigoLight,
    secondary = SecondaryVioletLight,
    tertiary = AccentTealLight,
    background = DeepCharcoal,
    surface = Slate800,
    onPrimary = PureWhite,
    onSecondary = Slate100,
    onBackground = Slate100,
    onSurface = PureWhite,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate500,
    outline = Slate600
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryIndigo,
    secondary = SecondaryViolet,
    tertiary = AccentTeal,
    background = Slate50,
    surface = PureWhite,
    onPrimary = PureWhite,
    onSecondary = DeepCharcoal,
    onBackground = DeepCharcoal,
    onSurface = DeepCharcoal,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate100
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Keep brand colors consistent
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
