package com.app.snapmind.domain.classify

import com.app.snapmind.domain.model.AppLanguage
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Tier 0 — čistě deterministické regexy: datum a čas, URL, telefon, částka, kód
 * objednávky nebo letu.
 *
 * Jediná vrstva, na které smí záviset připomínkový engine (§11.12), proto tu nesmí být
 * nic pravděpodobnostního. Když si Tier 0 není jistý kategorií, vrátí UNKNOWN a slovo
 * dostane Tier 1.
 *
 * Bez sítě, bez Android importů.
 */
class Tier0RegexClassifier(
    private val zone: ZoneId = ZoneId.systemDefault()
) : ContentClassifier {

    override fun classify(text: String, nowMillis: Long, language: AppLanguage): ClassificationResult {
        if (text.isBlank()) return ClassificationResult.EMPTY

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val time = findTime(text)
        val date = findDate(text, today, language)

        val signals = Signals(
            hasUrl = URL.containsMatchIn(text),
            hasPhone = PHONE.containsMatchIn(text) || PHONE_INTL.containsMatchIn(text),
            hasAmount = AMOUNT_SUFFIX.containsMatchIn(text) || AMOUNT_PREFIX.containsMatchIn(text),
            hasCode = FLIGHT.containsMatchIn(text) || ORDER_CODE.containsMatchIn(text),
            hasTime = time != null
        )

        // Bez času míří datum na poledne — půlnoc by u připomínky spadla na předchozí večer.
        val dateMillis = date?.let {
            LocalDateTime.of(it, time ?: LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        }

        return ClassificationResult(
            category = categoryFrom(signals, date),
            dateMillis = dateMillis,
            signals = signals
        )
    }

    /** Kategorii vrací jen tam, kde je signál jednoznačný. Jinak UNKNOWN → přebírá Tier 1. */
    private fun categoryFrom(signals: Signals, date: LocalDate?): DetectedCategory = when {
        signals.hasAmount && signals.hasCode -> DetectedCategory.PURCHASE
        signals.hasCode && date != null -> DetectedCategory.EVENT
        date != null && signals.hasTime -> DetectedCategory.EVENT
        signals.hasPhone -> DetectedCategory.CONTACT
        else -> DetectedCategory.UNKNOWN
    }

    // ---- datum -------------------------------------------------------------

    private fun findDate(text: String, today: LocalDate, language: AppLanguage): LocalDate? =
        isoDate(text)
            ?: czechMonthName(text, today)
            ?: englishMonthName(text, today)
            ?: numericDate(text, today, language)
            ?: relativeDate(text, today)

    private fun isoDate(text: String): LocalDate? =
        ISO.find(text)?.let { m ->
            safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }

    /**
     * Prochází všechny shody, ne jen první: "15. hodin ... 20. března" musí najít březen,
     * i když první shoda regexu byla slovo, které měsíc není.
     */
    private fun czechMonthName(text: String, today: LocalDate): LocalDate? =
        CZ_MONTH_DATE.findAll(text.lowercase()).firstNotNullOfOrNull { m ->
            val month = czMonthNumber(m.groupValues[2]) ?: return@firstNotNullOfOrNull null
            build(m.groupValues[1].toInt(), month, m.groupValues[3].toIntOrNull(), today)
        }

    private fun englishMonthName(text: String, today: LocalDate): LocalDate? {
        val lower = text.lowercase()
        EN_MONTH_FIRST.find(lower)?.let { m ->
            val month = EN_MONTHS.indexOf(m.groupValues[1]) + 1
            return build(m.groupValues[2].toInt(), month, m.groupValues[3].toIntOrNull(), today)
        }
        EN_DAY_FIRST.find(lower)?.let { m ->
            val month = EN_MONTHS.indexOf(m.groupValues[2]) + 1
            return build(m.groupValues[1].toInt(), month, m.groupValues[3].toIntOrNull(), today)
        }
        return null
    }

    private fun numericDate(text: String, today: LocalDate, language: AppLanguage): LocalDate? =
        NUMERIC.findAll(text).firstNotNullOfOrNull { m ->
            val first = m.groupValues[1].toInt()
            val separator = m.groupValues[2]
            val second = m.groupValues[3].toInt()
            val rawYear = m.groupValues[4].toIntOrNull()
            val year = when {
                rawYear == null -> null
                rawYear < 100 -> 2000 + rawYear
                else -> rawYear
            }
            val (day, month) = resolveDayMonth(first, second, separator, language)
            build(day, month, year, today)
        }

    /**
     * DATE FORMAT IS THE PAST. 01/02/2026 is 1 February in Czech and 2 January in American
     * English -- two numbers <= 12 have no format-agnostic reading. Resolution, in this exact
     * order, DO NOT "simplify" this to a single fixed order -- that is precisely the bug this
     * function exists to prevent:
     *
     *  1. If either number is > 12 it cannot be a month, so it must be the day -- the format is
     *     unambiguous and the language is irrelevant.
     *  2. A "." separator is ALWAYS DD.MM, regardless of language: the American convention
     *     never uses a dot.
     *  3. Only "/" with both numbers <= 12 is genuinely ambiguous. Only then does the app's
     *     language decide: EN -> MM/DD, CS -> DD/MM.
     */
    private fun resolveDayMonth(
        first: Int,
        second: Int,
        separator: String,
        language: AppLanguage
    ): Pair<Int, Int> = when {
        first > 12 -> first to second
        second > 12 -> second to first
        separator == "." -> first to second
        resolveLanguage(language) == AppLanguage.EN -> second to first
        else -> first to second
    }

    /** SYSTEM means the device's actual language, not the language chosen for the app's UI. */
    private fun resolveLanguage(language: AppLanguage): AppLanguage = when (language) {
        AppLanguage.SYSTEM -> if (Locale.getDefault().language == "cs") AppLanguage.CS else AppLanguage.EN
        else -> language
    }

    private fun relativeDate(text: String, today: LocalDate): LocalDate? {
        val lower = text.lowercase()
        RELATIVE_DAYS.forEach { (word, offset) ->
            if (wordRegex(word).containsMatchIn(lower)) return today.plusDays(offset)
        }
        WEEKDAYS.forEach { (word, dow) ->
            if (wordRegex(word).containsMatchIn(lower)) return today.with(TemporalAdjusters.next(dow))
        }
        return null
    }

    /**
     * Rok chybí často ("15. 3.", "koncert 15. března"). Bereme letošní, a když je takové
     * datum víc než den za námi, posouváme na příští rok — screenshot lístku pořízený
     * v prosinci míří na březen následujícího roku, ne na ten, co byl.
     */
    private fun build(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (year != null) return safeDate(year, month, day)
        val candidate = safeDate(today.year, month, day) ?: return null
        return if (candidate.isBefore(today.minusDays(1))) {
            safeDate(today.year + 1, month, day)
        } else {
            candidate
        }
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun findTime(text: String): LocalTime? =
        TIME.find(text)?.let { m ->
            runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
        }

    /** Delší prefixy se testují první, jinak "července" spadne pod "cerven". */
    private fun czMonthNumber(name: String): Int? {
        val n = stripDiacritics(name)
        return CZ_MONTHS.firstOrNull { (prefix, _) -> n.startsWith(prefix) }?.second
    }

    private fun stripDiacritics(value: String): String {
        val sb = StringBuilder(value.length)
        value.forEach { c -> sb.append(DIACRITICS[c] ?: c) }
        return sb.toString()
    }

    private fun wordRegex(word: String): Regex =
        wordCache.getOrPut(word) { Regex("""(?<!\p{L})${Regex.escape(word)}(?!\p{L})""") }

    private val wordCache = HashMap<String, Regex>()

    private companion object {
        val URL = Regex("""(https?://|www\.)\S{3,}""", RegexOption.IGNORE_CASE)

        // 9 číslic v českém členění, volitelně s předvolbou. Datum sem neprojde.
        val PHONE = Regex("""(?<!\d)(\+\d{1,3}\s?)?\d{3}\s?\d{3}\s?\d{3}(?!\d)""")

        // Mezinárodní formát: vždy s "+", jinak by se nedal rozeznat od čísla objednávky nebo
        // částky. Aspoň tři skupiny po "+předvolbě" drží spodní hranici na věrohodné délce čísla.
        val PHONE_INTL = Regex("""(?<!\d)\+\d{1,3}(?:[\s-]?\d{2,4}){3,5}(?!\d)""")

        val AMOUNT_SUFFIX = Regex(
            """(?<!\d)\d{1,3}(?:[ .]\d{3})*(?:[.,]\d{1,2})?\s?(kč|kc|czk|,-|eur|€|usd|\$|gbp|£)""",
            RegexOption.IGNORE_CASE
        )
        val AMOUNT_PREFIX = Regex(
            """(kč|czk|eur|€|usd|\$|gbp|£)\s?\d{1,3}(?:[ .]\d{3})*(?:[.,]\d{1,2})?""",
            RegexOption.IGNORE_CASE
        )

        /** Let: dvě velká písmena a 3–4 číslice, např. OK123, FR 1234. */
        val FLIGHT = Regex("""\b[A-Z]{2}\s?\d{3,4}\b""")

        /**
         * Kód objednávky bere jen v kontextu klíčového slova, jinak by chytal každý řetězec.
         * Klíčové slovo je case-insensitive, kód nikoli — a musí obsahovat číslici, aby
         * "ORDER CONFIRMED" neprošlo jako kód.
         */
        val ORDER_CODE = Regex(
            """(?i:objedn|rezerv|z[áa]silk|order|booking|tracking|reference)\p{L}*""" +
                """[\s\S]{0,25}?\b(?=[A-Z0-9-]{5,}\b)[A-Z0-9-]*\d[A-Z0-9-]*\b"""
        )

        val TIME = Regex("""(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?!\d)""")

        val ISO = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")

        // Oddělovač je vlastní skupina (2) -- resolveDayMonth podle něj (a podle jazyka)
        // rozhoduje mezi DD/MM a MM/DD, viz komentář u resolveDayMonth.
        val NUMERIC = Regex("""(?<!\d)(\d{1,2})\s*([./])\s*(\d{1,2})\s*[./]?\s*(\d{4}|\d{2})?(?!\d)""")

        val CZ_MONTH_DATE = Regex(
            """(?<!\d)(\d{1,2})\.?\s*([a-záčďéěíňóřšťúůýž]{3,12})(?:\s+(\d{4}))?"""
        )

        val EN_MONTHS = listOf(
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"
        )
        val EN_MONTH_FIRST = Regex(
            """\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?"""
        )
        val EN_DAY_FIRST = Regex(
            """\b(\d{1,2})(?:st|nd|rd|th)?\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?(?:,?\s+(\d{4}))?"""
        )

        /** Prefix → číslo měsíce, seřazeno od nejdelšího prefixu. */
        val CZ_MONTHS: List<Pair<String, Int>> = listOf(
            "cervenec" to 7, "cervence" to 7, "cervenc" to 7,
            "listopad" to 11, "prosin" to 12, "cerven" to 6,
            "unor" to 2, "brez" to 3, "kvet" to 5, "rijn" to 10, "rijen" to 10,
            "zari" to 9, "srp" to 8, "dub" to 4, "led" to 1
        ).sortedByDescending { it.first.length }

        val RELATIVE_DAYS = listOf(
            "dnes" to 0L, "today" to 0L,
            "zitra" to 1L, "zítra" to 1L, "tomorrow" to 1L,
            "pozitri" to 2L, "pozítří" to 2L,
            "pristi tyden" to 7L, "příští týden" to 7L, "next week" to 7L
        )

        val WEEKDAYS = listOf(
            "pondeli" to DayOfWeek.MONDAY, "pondělí" to DayOfWeek.MONDAY,
            "utery" to DayOfWeek.TUESDAY, "úterý" to DayOfWeek.TUESDAY,
            "streda" to DayOfWeek.WEDNESDAY, "středa" to DayOfWeek.WEDNESDAY,
            "stredu" to DayOfWeek.WEDNESDAY, "středu" to DayOfWeek.WEDNESDAY,
            "ctvrtek" to DayOfWeek.THURSDAY, "čtvrtek" to DayOfWeek.THURSDAY,
            "patek" to DayOfWeek.FRIDAY, "pátek" to DayOfWeek.FRIDAY,
            "sobota" to DayOfWeek.SATURDAY, "sobotu" to DayOfWeek.SATURDAY,
            "nedele" to DayOfWeek.SUNDAY, "neděli" to DayOfWeek.SUNDAY,
            "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY
        )

        val DIACRITICS: Map<Char, Char> = mapOf(
            'á' to 'a', 'č' to 'c', 'ď' to 'd', 'é' to 'e', 'ě' to 'e', 'í' to 'i',
            'ň' to 'n', 'ó' to 'o', 'ř' to 'r', 'š' to 's', 'ť' to 't', 'ú' to 'u',
            'ů' to 'u', 'ý' to 'y', 'ž' to 'z'
        )
    }
}
