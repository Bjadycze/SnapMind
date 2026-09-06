package com.app.snapmind.domain.usecase

import com.app.snapmind.domain.model.QuietHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    @Test
    fun `window wrapping past midnight covers both sides`() {
        val quiet = QuietHours(startHour = 22, endHour = 8)
        assertTrue(quiet.contains(23))
        assertTrue(quiet.contains(2))
        assertTrue(quiet.contains(7))
        assertFalse(quiet.contains(8))
        assertFalse(quiet.contains(18))
    }

    @Test
    fun `window inside one day behaves normally`() {
        val quiet = QuietHours(startHour = 9, endHour = 17)
        assertTrue(quiet.contains(12))
        assertFalse(quiet.contains(8))
        assertFalse(quiet.contains(17))
    }

    @Test
    fun `identical bounds mean no quiet hours at all`() {
        val quiet = QuietHours(startHour = 10, endHour = 10)
        (0..23).forEach { assertFalse(quiet.contains(it)) }
    }
}
