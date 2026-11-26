package com.servicebook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilsTest {

    @Test
    fun `car year validation accepts 1950 to 2030 inclusive`() {
        assertTrue(ValidationUtils.isValidCarYear("1950"))
        assertEquals(1950, ValidationUtils.parseValidCarYear("1950"))

        assertTrue(ValidationUtils.isValidCarYear("2014"))
        assertEquals(2014, ValidationUtils.parseValidCarYear("2014"))

        assertTrue(ValidationUtils.isValidCarYear("2030"))
        assertEquals(2030, ValidationUtils.parseValidCarYear("2030"))
    }

    @Test
    fun `car year validation rejects empty out-of-range and non-numeric values`() {
        assertFalse(ValidationUtils.isValidCarYear(""))
        assertNull(ValidationUtils.parseValidCarYear(""))

        assertFalse(ValidationUtils.isValidCarYear("   "))
        assertFalse(ValidationUtils.isValidCarYear("1949"))
        assertFalse(ValidationUtils.isValidCarYear("2031"))
        assertFalse(ValidationUtils.isValidCarYear("abcd"))
        assertFalse(ValidationUtils.isValidCarYear("20a4"))
        assertFalse(ValidationUtils.isValidCarYear("-2014"))
        assertFalse(ValidationUtils.isValidCarYear("2014.5"))
    }

    @Test
    fun `car year validation clears error after fixing invalid value`() {
        assertFalse(ValidationUtils.isValidCarYear("1949"))
        assertTrue(ValidationUtils.isValidCarYear("2014"))
    }

    @Test
    fun `maintenance due subtitle avoids double prepositions for months and years`() {
        assertEquals(
            "На 120 000 км · через 6 месяцев",
            ValidationUtils.formatMaintenanceDueSubtitle(120_000, "Через 6 месяцев", "120 000")
        )
        assertEquals(
            "На 120 000 км · через 1 год",
            ValidationUtils.formatMaintenanceDueSubtitle(120_000, "Через 1 год", "120 000")
        )
        assertEquals(
            "На 120 000 км · через 2 года",
            ValidationUtils.formatMaintenanceDueSubtitle(120_000, "Через 2 года", "120 000")
        )
    }

    @Test
    fun `maintenance due subtitle handles date-only mileage-only and prepositions`() {
        assertEquals(
            "На 120 000 км · до 20 декабря",
            ValidationUtils.formatMaintenanceDueSubtitle(120_000, "20 декабря", "120 000")
        )
        assertEquals(
            "На 120 000 км · до 20 декабря",
            ValidationUtils.formatMaintenanceDueSubtitle(120_000, "до 20 декабря", "120 000")
        )
        assertEquals(
            "На 120 000 км",
            ValidationUtils.formatMaintenanceDueSubtitle(120_000, "", "120 000")
        )
        assertEquals(
            "Через 6 месяцев",
            ValidationUtils.formatMaintenanceDueSubtitle(0, "Через 6 месяцев", "")
        )
        assertEquals(
            "До 20 декабря",
            ValidationUtils.formatMaintenanceDueSubtitle(0, "20 декабря", "")
        )
    }
}
