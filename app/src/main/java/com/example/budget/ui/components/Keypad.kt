package com.example.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.budget.ui.theme.Budget
import com.example.budget.ui.theme.TABULAR

/**
 * Rupiah keypad. Works on a digit string (no decimals — rupiah has no sen in practice).
 * "000" is one key because most prices end in thousands.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AmountKeypad(
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("000", "0", "⌫")
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Budget.colors.surface)
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (key == "⌫") onBackspace() else onDigits(key)
                                },
                                // Hold backspace to clear the whole amount
                                onLongClick = if (key == "⌫") ({
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onClear()
                                }) else null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete digit", tint = Budget.colors.ink)
                        } else {
                            Text(
                                key,
                                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                                color = Budget.colors.ink
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Applies a keypad press to a digit string, capped at 13 digits (≈ Rp 9,9 T). */
fun appendDigits(current: String, digits: String): String {
    val next = (current + digits).trimStart('0')
    return if (next.length > 13) current else next
}
