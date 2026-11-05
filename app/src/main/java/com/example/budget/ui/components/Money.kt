package com.example.budget.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.example.budget.ui.theme.Budget
import com.example.budget.ui.theme.TABULAR
import kotlin.math.abs
import kotlin.math.roundToLong

/** 1250000.0 → "1.250.000" (Indonesian grouping, whole rupiah) */
fun groupRupiah(amount: Double): String {
    val n = abs(amount).roundToLong()
    val s = n.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}

/** Plain text form for notifications, snackbars, accessibility: "Rp 1.250.000", "−Rp 5.000" */
fun rupiah(amount: Double, hidden: Boolean = false): String {
    if (hidden) return "Rp ••••••"
    val sign = if (amount < 0 && amount.roundToLong() != 0L) "−" else ""
    return "${sign}Rp ${groupRupiah(amount)}"
}

/** Compact form for tight spots: 1,2 jt · 850 rb */
fun rupiahShort(amount: Double): String {
    val a = abs(amount)
    val sign = if (amount < 0) "−" else ""
    return when {
        a >= 1_000_000_000 -> sign + trim(a / 1_000_000_000) + " M"
        a >= 1_000_000 -> sign + trim(a / 1_000_000) + " jt"
        a >= 1_000 -> sign + (a / 1_000).roundToLong() + " rb"
        else -> sign + a.roundToLong()
    }
}

private fun trim(v: Double): String {
    val r = (v * 10).roundToLong() / 10.0
    return if (r % 1.0 == 0.0) r.roundToLong().toString() else r.toString().replace('.', ',')
}

enum class SignMode { None, Auto, Always }

/**
 * Amount with a small, raised "Rp" and tabular digits.
 * The currency mark is set at [symbolScale] of the digit size so the number leads.
 */
@Composable
fun MoneyText(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    hidden: Boolean = false,
    sign: SignMode = SignMode.Auto,
    symbolScale: Float? = null,
    symbolColor: Color = Color.Unspecified,
    fontWeight: FontWeight? = null
) {
    val negative = amount < 0 && amount.roundToLong() != 0L
    val signText = when (sign) {
        SignMode.None -> ""
        SignMode.Auto -> if (negative) "−" else ""
        SignMode.Always -> if (negative) "−" else if (amount.roundToLong() != 0L) "+" else ""
    }
    // Big figures get a small raised "Rp"; list-size figures keep it legible and nearer the baseline
    val big = style.fontSize.isSp && style.fontSize.value >= 22f
    val scale = symbolScale ?: if (big) 0.55f else 0.78f
    val shift = if (big) 0.36f else 0.12f
    val text = buildAnnotatedString {
        if (signText.isNotEmpty()) append(signText)
        withStyle(
            SpanStyle(
                fontSize = scale.em,
                fontWeight = FontWeight.SemiBold,
                baselineShift = BaselineShift(shift),
                color = symbolColor
            )
        ) { append("Rp") }
        append(" ") // thin space
        append(if (hidden) "••••••" else groupRupiah(amount))
    }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontFeatureSettings = TABULAR),
        color = if (color == Color.Unspecified) Budget.colors.ink else color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false
    )
}

/**
 * Thousands dots for a digit string, with a cursor map in both directions.
 * "1250000" → "1.250.000". Used to show dots while the field itself only stores digits,
 * so the cursor stays where you type.
 */
class DigitGrouping(digits: String) {
    val text: String
    /** cursor position in digits → position in [text] */
    val toShown: IntArray
    /** cursor position in [text] → position in digits */
    val toDigits: IntArray

    init {
        val n = digits.length
        val out = StringBuilder()
        val o2t = IntArray(n + 1)
        val t2o = ArrayList<Int>(n + n / 3 + 1)
        for (i in 0 until n) {
            if (i > 0 && (n - i) % 3 == 0) { t2o.add(i); out.append('.') }
            o2t[i] = out.length
            t2o.add(i)
            out.append(digits[i])
        }
        o2t[n] = out.length
        t2o.add(n)
        text = out.toString()
        toShown = o2t
        toDigits = t2o.toIntArray()
    }
}

/** Shows "1.250.000" for a field whose value is "1250000". */
object RupiahGrouping : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val g = DigitGrouping(text.text)
        return androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString(g.text),
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int) = g.toShown[offset.coerceIn(0, g.toShown.size - 1)]
                override fun transformedToOriginal(offset: Int) = g.toDigits[offset.coerceIn(0, g.toDigits.size - 1)]
            }
        )
    }
}
