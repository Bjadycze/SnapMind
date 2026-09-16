package com.app.snapmind.domain.classify

import com.app.snapmind.domain.model.AppLanguage

/**
 * Hrubá kategorie odvozená z textu položky (OCR výstup nebo sdílený text).
 *
 * Kategorie pouze řadí seznam. Nic na ní nezávisí — chybná kategorie je levná (§8, Task 7).
 * Ukládá se jako NÁZEV enumu, nikdy ordinal (stejné pravidlo jako CaptureSource, §6.1).
 */
enum class DetectedCategory {
    RECIPE,
    EVENT,
    PURCHASE,
    ARTICLE,
    CONTACT,
    UNKNOWN
}

/**
 * Deterministické signály z Tier 0. Neukládají se do databáze — slouží jen k volbě
 * kategorie a k testům.
 */
data class Signals(
    val hasUrl: Boolean = false,
    val hasPhone: Boolean = false,
    val hasAmount: Boolean = false,
    val hasCode: Boolean = false,
    val hasTime: Boolean = false
)

data class ClassificationResult(
    val category: DetectedCategory,
    /** Epoch millis, UTC. Null, když v textu není žádné rozpoznatelné datum. */
    val dateMillis: Long?,
    val signals: Signals = Signals()
) {
    companion object {
        val EMPTY = ClassificationResult(DetectedCategory.UNKNOWN, null)
    }
}

/**
 * Klasifikace běží výhradně na zařízení a nikdy nesahá na síť (§11.12, Tier 0 a 1).
 * Implementace musí být čistá funkce textu — žádný Android framework, aby šla testovat
 * obyčejným JUnit testem.
 */
interface ContentClassifier {
    /**
     * [language] only matters for Tier 0's day/month disambiguation on an ambiguous slash
     * date (spec.md 11.14 follow-up) -- SYSTEM resolves to the device's actual language, not
     * whatever the user picked for the app's own UI (AppLanguagePrefs / AppLocale.kt).
     */
    fun classify(
        text: String,
        nowMillis: Long = System.currentTimeMillis(),
        language: AppLanguage = AppLanguage.SYSTEM
    ): ClassificationResult
}
