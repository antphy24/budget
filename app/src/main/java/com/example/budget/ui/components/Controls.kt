package com.example.budget.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.budget.ui.theme.Budget

/** The one strong action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = Budget.colors.emerald,
    contentColor: Color = Budget.colors.onEmerald
) {
    val c = Budget.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else c.sunk)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) contentColor else c.inkFaint)
        }
    }
}

/** Secondary action on a sunk background. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentColor: Color = Budget.colors.ink,
    background: Color = Budget.colors.sunk
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/** Pill chip for one-tap choices (accounts, pockets, categories). */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val c = Budget.colors
    val bg by animateColorAsState(if (selected) c.ink else c.surface, label = "chipBg")
    val fg by animateColorAsState(if (selected) c.paper else c.ink, label = "chipFg")
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(bg)
            .border(1.dp, if (selected) c.ink else c.line, RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** Segmented control: sunk track, raised white thumb on the chosen option. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Budget.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.sunk)
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val isSel = option == selected
            val bg by animateColorAsState(if (isSel) c.surface else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) c.ink else c.inkSoft,
                    maxLines = 1
                )
            }
        }
    }
}

/** Small status tag (e.g. "On track", "Spending fast"). */
@Composable
fun StatusTag(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** Text input as a sunk well with an optional prefix ("Rp") or suffix ("%"). */
@Composable
fun FieldWell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    suffix: String? = null,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    val c = Budget.colors
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.emerald),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (keyboardType == androidx.compose.ui.text.input.KeyboardType.Text)
                androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
            else androidx.compose.ui.text.input.KeyboardCapitalization.None
        ),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.sunk)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prefix != null) {
                    Text(prefix, style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.inkFaint)
                    inner()
                }
                if (suffix != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(suffix, style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                }
            }
        }
    )
}

/** Small caption above a field or control. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Budget.colors.inkSoft,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

/** A labelled, horizontally scrolling row of chips. */
@Composable
fun ChipRow(label: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.padding(bottom = 14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Budget.colors.inkSoft,
            modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp)
        )
        Row(
            Modifier
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * Rupiah amount input. Holds digits only ("1250000") and shows dots ("1.250.000") on screen,
 * so typing, deleting and moving the cursor behave normally.
 */
@Composable
fun MoneyField(
    digits: String,
    onDigits: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0"
) {
    FieldWell(
        value = digits,
        onValueChange = { v -> onDigits(v.filter { it.isDigit() }.trimStart('0').take(13)) },
        modifier = modifier,
        placeholder = placeholder,
        prefix = "Rp",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        visualTransformation = RupiahGrouping
    )
}
