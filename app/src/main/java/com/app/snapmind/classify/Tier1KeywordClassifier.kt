package com.app.snapmind.domain.classify

/**
 * Tier 1 — hrubá kategorie z klíčových slov a struktury. Na zařízení, bez sítě.
 *
 * Chybná odpověď je tu levná: kategorie jen řadí seznam, nikdo podle ní nic nespouští.
 * Proto stačí prosté skóre a pevné pořadí při shodě, žádný model.
 *
 * Datum neurčuje — to je výhradně práce Tier 0.
 */
class Tier1KeywordClassifier : ContentClassifier {

    override fun classify(text: String, nowMillis: Long): ClassificationResult {
        if (text.isBlank()) return ClassificationResult.EMPTY
        val lower = text.lowercase()

        val best = KEYWORDS
            .map { (category, words) -> category to words.count { lower.contains(it) } }
            .filter { it.second > 0 }
            .maxWithOrNull(
                // Vyšší skóre vyhrává; při shodě vyhrává nižší index v PRIORITY.
                compareBy<Pair<DetectedCategory, Int>> { it.second }
                    .thenBy { -PRIORITY.indexOf(it.first) }
            )

        return ClassificationResult(
            category = best?.first ?: DetectedCategory.UNKNOWN,
            dateMillis = null
        )
    }

    private companion object {
        /** Při stejném skóre vyhraje kategorie výš v tomhle pořadí. */
        val PRIORITY = listOf(
            DetectedCategory.RECIPE,
            DetectedCategory.EVENT,
            DetectedCategory.PURCHASE,
            DetectedCategory.CONTACT,
            DetectedCategory.ARTICLE,
            DetectedCategory.UNKNOWN
        )

        val KEYWORDS: Map<DetectedCategory, List<String>> = mapOf(
            DetectedCategory.RECIPE to listOf(
                "recept", "ingredience", "postup:", "lžíce", "lzice", "lžička", "lzicka",
                "těsto", "testo", "uvařit", "uvarit", "upéct", "upect", "troubě", "troube",
                "gramů", "gramu", " ml ", " dl ", "recipe", "ingredients", "tbsp", "tsp",
                "preheat", "bake", "servings"
            ),
            DetectedCategory.EVENT to listOf(
                "koncert", "vstupenka", "vstupenky", "lístek", "listek", "rezervace",
                "sraz", "schůzka", "schuzka", "festival", "výstava", "vystava", "divadlo",
                "premiéra", "premiera", "otevírá", "otevira", "ticket", "tickets", "event",
                "doors open", "rsvp", "meeting", "webinar"
            ),
            DetectedCategory.PURCHASE to listOf(
                "objednávk", "objednavk", "košík", "kosik", "doprava zdarma", "sleva",
                "akce", "faktura", "zaplat", "cena", "skladem", "zásilk", "zasilk",
                "order", "cart", "checkout", "shipping", "invoice", "discount", "price",
                "add to bag"
            ),
            DetectedCategory.ARTICLE to listOf(
                "článek", "clanek", "přečíst", "precist", "rozhovor", "návod", "navod",
                "blog", "epizoda", "díl ", "dil ", "podcast", "youtube", "reels",
                "article", "read later", "interview", "tutorial", "thread", "watch"
            ),
            DetectedCategory.CONTACT to listOf(
                "telefon", "kontakt", "vizitka", "e-mail", "email", "@", "adresa",
                "zavolat", "volat", "napsat mu", "napsat jí", "phone", "contact",
                "address", "call me"
            )
        )
    }
}
