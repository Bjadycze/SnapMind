package com.app.snapmind.domain.classify

import com.app.snapmind.domain.model.AppLanguage
import javax.inject.Inject

/**
 * Řetězí Tier 0 a Tier 1 podle jistoty: datum bere vždy z Tier 0, kategorii z Tier 0
 * jen tehdy, když si byl jistý. Jinak dostane slovo Tier 1, a URL bez jiného signálu
 * spadne na ARTICLE.
 *
 * Tohle je jediná implementace, kterou zbytek aplikace vidí (§8, Task 7).
 */
class ChainedContentClassifier @Inject constructor(
    private val tier0: Tier0RegexClassifier,
    private val tier1: Tier1KeywordClassifier
) : ContentClassifier {

    override fun classify(text: String, nowMillis: Long, language: AppLanguage): ClassificationResult {
        if (text.isBlank()) return ClassificationResult.EMPTY

        val base = tier0.classify(text, nowMillis, language)
        if (base.category != DetectedCategory.UNKNOWN) return base

        val keyword = tier1.classify(text, nowMillis, language).category
        val category = when {
            keyword != DetectedCategory.UNKNOWN -> keyword
            base.signals.hasUrl -> DetectedCategory.ARTICLE
            else -> DetectedCategory.UNKNOWN
        }
        return base.copy(category = category)
    }
}
