package com.example.budget.ui

import com.example.budget.ui.components.DigitGrouping
import org.junit.Assert.assertEquals
import org.junit.Test

class DigitGroupingTest {
    @Test fun shownText() {
        assertEquals("", DigitGrouping("").text)
        assertEquals("950", DigitGrouping("950").text)
        assertEquals("1.250", DigitGrouping("1250").text)
        assertEquals("1.250.000", DigitGrouping("1250000").text)
    }

    @Test fun cursorAtEndStaysAtEnd() {
        val g = DigitGrouping("1250000")
        assertEquals(9, g.toShown[7])           // after the last digit
        assertEquals(7, g.toDigits[9])
    }

    @Test fun cursorMapsAroundDots() {
        val g = DigitGrouping("1250000")        // shown "1.250.000"
        assertEquals(0, g.toShown[0])
        assertEquals(2, g.toShown[1])            // after "1" → after the dot, before "2"
        assertEquals(1, g.toDigits[1])           // between "1" and "." → after "1"
        assertEquals(1, g.toDigits[2])
        assertEquals(4, g.toDigits[6])           // "1.250.|000"
    }

    @Test fun roundTrip() {
        for (s in listOf("5", "25000", "1250000", "123456789012")) {
            val g = DigitGrouping(s)
            for (i in 0..s.length) assertEquals("$s @ $i", i, g.toDigits[g.toShown[i]])
        }
    }
}
