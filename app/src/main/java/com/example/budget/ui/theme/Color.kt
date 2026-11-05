package com.example.budget.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Budget's own colour tokens. Material roles are derived from these in Theme.kt;
 * screens use [BudgetColors] directly for anything money-specific.
 */
@Immutable
data class BudgetColors(
    /** Page background */
    val paper: Color,
    /** Raised groups (lists, sheets) */
    val surface: Color,
    /** Inset areas: tracks, input wells, keypad keys */
    val sunk: Color,
    /** Primary text */
    val ink: Color,
    /** Secondary text, captions */
    val inkSoft: Color,
    /** Tertiary text, disabled */
    val inkFaint: Color,
    /** Hairlines between rows */
    val line: Color,
    /** Brand + money coming in */
    val emerald: Color,
    /** Text/icons on emerald */
    val onEmerald: Color,
    /** Quiet emerald wash for selected chips, icon circles */
    val emeraldTint: Color,
    /** The hero card (deep emerald in both themes) */
    val hero: Color,
    val onHero: Color,
    val onHeroSoft: Color,
    /** Spending faster than time passes */
    val amber: Color,
    val amberTint: Color,
    /** Overspent / almost empty / destructive */
    val ruby: Color,
    val rubyTint: Color,
    val isDark: Boolean
)

val LightBudgetColors = BudgetColors(
    paper = Color(0xFFF4F5F1),
    surface = Color(0xFFFFFFFF),
    sunk = Color(0xFFEAEDE7),
    ink = Color(0xFF17201C),
    inkSoft = Color(0xFF5A6560),
    inkFaint = Color(0xFF98A29C),
    line = Color(0xFFE3E7E1),
    emerald = Color(0xFF0D6B53),
    onEmerald = Color(0xFFFFFFFF),
    emeraldTint = Color(0xFFDDEEE6),
    hero = Color(0xFF0B4A3B),
    onHero = Color(0xFFFFFFFF),
    onHeroSoft = Color(0xFFA9CFC2),
    amber = Color(0xFFB06F0A),
    amberTint = Color(0xFFF7EBD6),
    ruby = Color(0xFFB0312A),
    rubyTint = Color(0xFFF6E0DD),
    isDark = false
)

val DarkBudgetColors = BudgetColors(
    paper = Color(0xFF0E1412),
    surface = Color(0xFF161D1A),
    sunk = Color(0xFF1E2724),
    ink = Color(0xFFE7ECE9),
    inkSoft = Color(0xFF9AA6A0),
    inkFaint = Color(0xFF65706B),
    line = Color(0xFF242E2A),
    emerald = Color(0xFF5BC6A3),
    onEmerald = Color(0xFF04271E),
    emeraldTint = Color(0xFF173A30),
    hero = Color(0xFF0F4235),
    onHero = Color(0xFFF1F7F4),
    onHeroSoft = Color(0xFF8FBFAF),
    amber = Color(0xFFE2A94C),
    amberTint = Color(0xFF3A2C14),
    ruby = Color(0xFFEA7C72),
    rubyTint = Color(0xFF3D1E1B),
    isDark = true
)

val LocalBudgetColors = staticCompositionLocalOf { LightBudgetColors }
