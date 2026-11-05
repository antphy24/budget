package com.example.budget.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.budget.domain.budget.PaceStatus
import com.example.budget.ui.theme.Budget

/**
 * The pocket's signature gauge.
 *
 *  ██████████████░░░░░░░░░░   fill   = share of this cycle's money still left
 *               ▏             tick   = share that *should* still be left today
 *
 * Fill behind the tick → spending faster than time passes.
 */
@Composable
fun PaceRail(
    remainingFraction: Float,
    expectedRemainingFraction: Float,
    status: PaceStatus,
    modifier: Modifier = Modifier,
    trackColor: Color = Budget.colors.sunk,
    tickColor: Color = Budget.colors.ink
) {
    val c = Budget.colors
    val fillColor = when (status) {
        PaceStatus.OK -> c.emerald
        PaceStatus.FAST -> c.amber
        PaceStatus.LOW, PaceStatus.OVERSPENT -> c.ruby
    }
    val fill by animateFloatAsState(remainingFraction.coerceIn(0f, 1f), tween(600), label = "rail")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2
        val r = CornerRadius(barH / 2, barH / 2)
        drawRoundRect(trackColor, topLeft = Offset(0f, top), size = Size(size.width, barH), cornerRadius = r)
        if (fill > 0f) {
            drawRoundRect(
                fillColor,
                topLeft = Offset(0f, top),
                size = Size((size.width * fill).coerceAtLeast(barH), barH),
                cornerRadius = r
            )
        }
        val x = (size.width * expectedRemainingFraction.coerceIn(0f, 1f))
            .coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
        drawLine(
            tickColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}
