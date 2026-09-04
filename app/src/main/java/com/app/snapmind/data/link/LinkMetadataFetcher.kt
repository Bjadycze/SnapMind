package com.app.snapmind.data.link

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LinkMetadata(
    val url: String,
    val title: String?,
    val imageUrl: String?
)

/**
 * Turns a shared link into something identifiable a week later.
 *
 * A shared reel arrives as bare text: no thumbnail, nothing for OCR to read, so the card is
 * indistinguishable from every other card (spec.md 11.6). Open Graph tags close that gap.
 *
 * Failure is expected and silent. Instagram and Facebook frequently answer a logged-out
 * request with a login page, in which case the item stays exactly as it is today.
 */
@Singleton
class LinkMetadataFetcher @Inject constructor(
    private val client: OkHttpClient
) {

    suspend fun fetch(url: String): LinkMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            if (isYouTube(url)) fetchOEmbed(url) ?: fetchOpenGraph(url)
            else fetchOpenGraph(url)
        }.getOrNull()
    }

    private fun isYouTube(url: String) =
        url.contains("youtube.com", true) || url.contains("youtu.be", true)

    /** YouTube publishes oEmbed without auth, so it answers reliably where scraping does not. */
    private fun fetchOEmbed(url: String): LinkMetadata? {
        val endpoint = "https://www.youtube.com/oembed?format=json&url=" +
                java.net.URLEncoder.encode(url, "UTF-8")

        val body = get(endpoint) ?: return null
        val json = JSONObject(body)
        return LinkMetadata(
            url = url,
            title = json.optString("title").takeIf { it.isNotBlank() },
            imageUrl = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
        )
    }

    private fun fetchOpenGraph(url: String): LinkMetadata? {
        val html = get(url) ?: return null
        return LinkMetadata(
            url = url,
            title = metaContent(html, "og:title") ?: titleTag(html),
            imageUrl = metaContent(html, "og:image")
        ).takeIf { it.title != null || it.imageUrl != null }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            // Without a browser UA most sites return a stub page with no OG tags at all.
            .header(
                "User-Agent",
                "Mozilla/5.0 (compatible; SnapMind/1.0; +https://example.invalid)"
            )
            .header("Accept-Language", "cs,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            // Only the head matters; whole pages are megabytes and the tags sit up top.
            val source = response.body?.source() ?: return null
            source.request(MAX_BYTES)
            return source.buffer.snapshot().utf8()
        }
    }

    private fun metaContent(html: String, property: String): String? {
        val pattern = Regex(
            """<meta[^>]+(?:property|name)=["']$property["'][^>]*content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val reversed = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["']$property["']""",
            RegexOption.IGNORE_CASE
        )
        val raw = pattern.find(html)?.groupValues?.get(1)
            ?: reversed.find(html)?.groupValues?.get(1)
        return raw?.let { decodeEntities(it) }?.takeIf { it.isNotBlank() }
    }

    private fun titleTag(html: String): String? =
        Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.let { decodeEntities(it) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /**
     * Instagram writes captions as numeric entities (`&#x11b;`), so a named-entity table alone
     * turns every Czech word into visible escape codes.
     */
    private fun decodeEntities(value: String): String {
        val named = value
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

        val numeric = Regex("""&#(x?)([0-9a-fA-F]+);""").replace(named) { match ->
            val radix = if (match.groupValues[1].isNotEmpty()) 16 else 10
            match.groupValues[2].toIntOrNull(radix)
                ?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }

        // Ampersand last: decoding it first would turn "&amp;#39;" into a live entity.
        return numeric.replace("&amp;", "&")
    }

    private companion object {
        const val MAX_BYTES = 120_000L
    }
}