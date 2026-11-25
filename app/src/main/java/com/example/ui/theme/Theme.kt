package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val YoutDarkColorScheme = darkColorScheme(
  primary = ElectricBlue,
  onPrimary = PureWhite,
  primaryContainer = MidnightCardElevated,
  onPrimaryContainer = PureWhite,
  secondary = CyanGlow,
  onSecondary = MidnightBg,
  secondaryContainer = MidnightCard,
  onSecondaryContainer = PureWhite,
  tertiary = EmeraldLive,
  onTertiary = MidnightBg,
  background = MidnightBg,
  onBackground = PureWhite,
  surface = MidnightSurface,
  onSurface = PureWhite,
  surfaceVariant = MidnightCard,
  onSurfaceVariant = SlateTextMuted,
  outline = MidnightOutline,
  error = CoralAlert,
  onError = PureWhite,
)

@Composable
fun YoutTheme(
  isBangla: Boolean = true,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = YoutDarkColorScheme,
    typography = youtTypography(isBangla),
    content = content,
  )
}
