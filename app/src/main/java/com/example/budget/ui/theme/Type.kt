package com.example.budget.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.budget.R

/** Plus Jakarta Sans — one family for everything; tabular figures for money. */
val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_extrabold, FontWeight.ExtraBold)
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

/**
 * Scale (sp): 11 · 12 · 13 · 15 · 17 · 20 · 24 · 30 · 40
 * Body is 15 rather than Material's 16 with tighter tracking — denser, calmer lists.
 */
val Typography = Typography(
    displayLarge = style(40, 46, FontWeight.ExtraBold, -1.2),
    displayMedium = style(30, 36, FontWeight.ExtraBold, -0.8),
    displaySmall = style(24, 30, FontWeight.Bold, -0.5),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.8),
    headlineMedium = style(24, 30, FontWeight.Bold, -0.5),
    headlineSmall = style(20, 26, FontWeight.Bold, -0.3),
    titleLarge = style(20, 26, FontWeight.Bold, -0.3),
    titleMedium = style(17, 22, FontWeight.SemiBold, -0.2),
    titleSmall = style(15, 20, FontWeight.SemiBold, -0.1),
    bodyLarge = style(15, 22, FontWeight.Normal, -0.1),
    bodyMedium = style(13, 18, FontWeight.Normal, 0.0),
    bodySmall = style(12, 16, FontWeight.Normal, 0.1),
    labelLarge = style(15, 20, FontWeight.SemiBold, -0.1),
    labelMedium = style(13, 16, FontWeight.Medium, 0.0),
    labelSmall = style(11, 14, FontWeight.Medium, 0.2)
)

/** Money digits: tabular so columns of amounts line up. */
const val TABULAR = "tnum"
