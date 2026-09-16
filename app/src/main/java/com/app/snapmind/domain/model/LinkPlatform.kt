package com.app.snapmind.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Where a shared item came from, derived from its URL.
 *
 * Shared links all look alike on a card once they have a thumbnail, so the source has to be
 * readable at a glance — otherwise the thumbnail just replaces one kind of anonymity with
 * another.
 */
enum class LinkPlatform(val label: String, val accent: Color) {
    YOUTUBE("YouTube", Color(0xFFFF4E45)),
    INSTAGRAM("Instagram", Color(0xFFE1568A)),
    FACEBOOK("Facebook", Color(0xFF4E8CF5)),
    TIKTOK("TikTok", Color(0xFF4ED8D0)),
    X("X", Color(0xFFB4B2A9)),
    REDDIT("Reddit", Color(0xFFFF7A45)),
    // Not a brand name, so unlike the others this has no fixed label -- the display site
    // resolves it through R.string.link_platform_other instead of reading this field.
    OTHER("", Color(0xFFAFA9EC));

    companion object {
        /** Returns null when the item is a screenshot: extractedText is OCR output, not a URL. */
        fun from(extractedText: String): LinkPlatform? {
            val url = Regex("""^https?://\S+""").find(extractedText.trim())?.value ?: return null
            val host = runCatching { java.net.URI(url).host.orEmpty() }
                .getOrDefault("")
                .removePrefix("www.")
                .lowercase()

            return when {
                host.contains("youtube") || host.contains("youtu.be") -> YOUTUBE
                host.contains("instagram") -> INSTAGRAM
                host.contains("facebook") || host.contains("fb.watch") -> FACEBOOK
                host.contains("tiktok") -> TIKTOK
                host == "x.com" || host.contains("twitter") -> X
                host.contains("reddit") -> REDDIT
                host.isNotBlank() -> OTHER
                else -> null
            }
        }
    }
}