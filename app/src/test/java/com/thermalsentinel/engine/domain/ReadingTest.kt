package com.thermalsentinel.engine.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {

    private val present = Reading.Present(38.5f)
    private val absent = Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)

    @Test
    fun presentCarriesItsValue() {
        assertEquals(38.5f, present.valueOrNull!!, 0.001f)
        assertTrue(present.isPresent)
        assertNull(present.absenceReason)
    }

    @Test
    fun absentCarriesItsReasonAndNoValue() {
        assertNull(absent.valueOrNull)
        assertFalse(absent.isPresent)
        assertEquals(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE, absent.absenceReason)
    }

    @Test
    fun absentIsCovariantSoItCanStandInForAnyReading() {
        val asFloat: Reading<Float> = absent
        assertNull(asFloat.valueOrNull)

        val readings: List<Reading<Float>> = listOf(present, absent)
        assertEquals(1, readings.count { it.isPresent })
    }

    @Test
    fun orElseSuppliesAFallbackWithoutHidingAbsence() {
        assertEquals(38.5f, present.orElse(0f), 0.001f)
        assertEquals(0f, absent.orElse(0f), 0.001f)
    }

    @Test
    fun asReadingWrapsAPlatformValue() {
        assertTrue(40f.asReading().isPresent)
        assertEquals(40f, 40f.asReading().valueOrNull!!, 0.001f)
    }

    @Test
    fun everyAbsenceReasonHasItsOwnDisplayLabel() {
        val labels = AbsenceReason.entries.map { it.displayLabel }
        assertEquals(AbsenceReason.entries.size, labels.toSet().size)
        assertTrue(labels.none { it.isBlank() })
        assertFalse(AbsenceReason.NOT_COLLECTED_YET.displayLabel == AbsenceReason.NOT_RECORDED.displayLabel)
    }
}
