package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Keyless discovery of visible track links on a public catalog page.
 * This is NOT a full-site search: only the fetched index page is examined.
 * A visible download button is not evidence of redistribution rights.
 */
class HtmlCatalogSource(
    override val id: String,
    private val homeUrl: String,
    private val trackPath: Regex
) : MusicSource {
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        if (terms.isEmpty()) return@withContext emptyList()
        val document = Jsoup.connect(homeUrl)
            .userAgent("Muzykoisk/0.1 (+Android; public catalog search)")
            .timeout(6500)
            .maxBodySize(900_000)
            .get()
        document.select("a[href]").asSequence().mapNotNull { link ->
            val url = link.absUrl("href")
            val title = link.text().trim()
            if (!url.startsWith(homeUrl) || !trackPath.containsMatchIn(url) ||
                title.length !in 3..160 || !terms.all { title.lowercase().contains(it) }
            ) null else Track(title = title, artist = "", sourceId = id, pageUrl = url)
        }.distinctBy { it.pageUrl }.take(25).toList()
    }
}
