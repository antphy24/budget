package com.example.budget.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private fun materialScheme(c: BudgetColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.emerald, onPrimary = c.onEmerald,
        primaryContainer = c.emeraldTint, onPrimaryContainer = c.ink,
        secondary = c.inkSoft, onSecondary = c.paper,
        secondaryContainer = c.sunk, onSecondaryContainer = c.ink,
        tertiary = c.amber, onTertiary = c.paper,
        background = c.paper, onBackground = c.ink,
        surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.inkSoft,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        outline = c.inkFaint, outlineVariant = c.line,
        error = c.ruby, onError = c.paper, errorContainer = c.rubyTint, onErrorContainer = c.ink,
        scrim = Color.Black
    )
} else {
    lightColorScheme(
        primary = c.emerald, onPrimary = c.onEmerald,
        primaryContainer = c.emeraldTint, onPrimaryContainer = c.ink,
        secondary = c.inkSoft, onSecondary = c.surface,
        secondaryContainer = c.sunk, onSecondaryContainer = c.ink,
        tertiary = c.amber, onTertiary = c.surface,
        background = c.paper, onBackground = c.ink,
        surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.inkSoft,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.sunk,
        outline = c.inkFaint, outlineVariant = c.line,
        error = c.ruby, onError = c.surface, errorContainer = c.rubyTint, onErrorContainer = c.ink,
        scrim = Color.Black
    )
}

/** Radius scale: 8 small controls · 14 groups and chips · 22 hero and sheets */
private val BudgetShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BudgetTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic (wallpaper) colour is intentionally off: the app keeps its own identity.
    val colors = if (useDarkTheme) DarkBudgetColors else LightBudgetColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = colors.paper.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDarkTheme
            controller.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(LocalBudgetColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme(colors),
            typography = Typography,
            shapes = BudgetShapes,
            content = content
        )
    }
}

/** Shorthand: `Budget.colors.emerald` */
object Budget {
    val colors: BudgetColors
        @Composable @ReadOnlyComposable
        get() = LocalBudgetColors.current
}
