package com.app.snapmind.domain.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Čistý JUnit test — klasifikace nesmí mít žádnou závislost na Androidu ani na síti.
 */
class ContentClassifierTest {

    private val zone = ZoneId.of("Europe/Prague")
    private val tier0 = Tier0RegexClassifier(zone)
    private val chain = ChainedContentClassifier(tier0, Tier1KeywordClassifier())

    /** 10. 1. 2026, 10:00 Praha. */
    private val now = millis(LocalDate.of(2026, 1, 10), LocalTime.of(10, 0))

    private fun millis(date: LocalDate, time: LocalTime) =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    // ---- datum -------------------------------------------------------------

    @Test
    fun `czech numeric date without year resolves to this year at noon`() {
        val result = tier0.classify("Sraz 15. 3. u kina", now)
        assertEquals(millis(LocalDate.of(2026, 3, 15), LocalTime.NOON), result.dateMillis)
    }

    @Test
    fun `czech month name with time`() {
        val result = tier0.classify("Koncert 20. března 2026 v 19:30", now)
        assertEquals(millis(LocalDate.of(2026, 3, 20), LocalTime.of(19, 30)), result.dateMillis)
    }

    @Test
    fun `czech july is not read as june`() {
        val result = tier0.classify("Dovolená od 3. července", now)
        assertEquals(millis(LocalDate.of(2026, 7, 3), LocalTime.NOON), result.dateMillis)
    }

    @Test
    fun `english month name`() {
        val result = tier0.classify("Deadline March 15, 2026", now)
        assertEquals(millis(LocalDate.of(2026, 3, 15), LocalTime.NOON), result.dateMillis)
    }

    @Test
    fun `iso date wins over everything else`() {
        val result = tier0.classify("valid 2026-05-01, tisk 3.4.", now)
        assertEquals(millis(LocalDate.of(2026, 5, 1), LocalTime.NOON), result.dateMillis)
    }

    @Test
    fun `relative tomorrow`() {
        val result = tier0.classify("zítra vyzvednout balík", now)
        assertEquals(millis(LocalDate.of(2026, 1, 11), LocalTime.NOON), result.dateMillis)
    }

    @Test
    fun `missing year rolls forward when the date already passed`() {
        val december = millis(LocalDate.of(2026, 12, 20), LocalTime.of(9, 0))
        val result = tier0.classify("Lístek na 15. 3.", december)
        assertEquals(millis(LocalDate.of(2027, 3, 15), LocalTime.NOON), result.dateMillis)
    }

    @Test
    fun `text without a date has no date`() {
        assertNull(tier0.classify("jen poznámka bez data", now).dateMillis)
    }

    @Test
    fun `phone number is not parsed as a date`() {
        val result = tier0.classify("Zavolat +420 777 123 456", now)
        assertNull(result.dateMillis)
        assertEquals(DetectedCategory.CONTACT, result.category)
    }

    // ---- kategorie ---------------------------------------------------------

    @Test
    fun `amount plus order code is a purchase`() {
        val result = chain.classify("Objednávka AB12345 celkem 1 299 Kč", now)
        assertEquals(DetectedCategory.PURCHASE, result.category)
    }

    @Test
    fun `date with time is an event`() {
        val result = chain.classify("Divadlo 20. 3. 2026 19:00", now)
        assertEquals(DetectedCategory.EVENT, result.category)
    }

    @Test
    fun `recipe keywords win over a bare url`() {
        val result = chain.classify(
            "Recept na chleba: 500 g mouky, 2 lžíce oleje, péct v troubě. https://example.com",
            now
        )
        assertEquals(DetectedCategory.RECIPE, result.category)
    }

    @Test
    fun `url alone falls back to article`() {
        val result = chain.classify("https://example.com/neco", now)
        assertEquals(DetectedCategory.ARTICLE, result.category)
    }

    @Test
    fun `english recipe sample`() {
        val result = chain.classify("Recipe: 2 tbsp olive oil, preheat oven, bake 20 min", now)
        assertEquals(DetectedCategory.RECIPE, result.category)
    }

    @Test
    fun `unrecognised text stays unknown`() {
        val result = chain.classify("asdf qwerty", now)
        assertEquals(DetectedCategory.UNKNOWN, result.category)
        assertNull(result.dateMillis)
    }

    @Test
    fun `blank text is empty`() {
        assertEquals(ClassificationResult.EMPTY, chain.classify("   ", now))
    }
}
