package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Search-page parser. A search URL must be confirmed against the site's public form
 * before this adapter is enabled. No guessed endpoints or anti-bot bypass.
 */
class SiteSearchSource(
    override val id: String,
    private val searchUrlPrefix: String,
    private val host: String,
    private val trackPath: Regex
) : MusicSource {
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = searchUrlPrefix + URLEncoder.encode(query.trim(), "UTF-8")
        val doc = Jsoup.connect(url).timeout(7000).maxBodySize(1_000_000).get()
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        doc.select("a[href]").mapNotNull { a ->
            val href = a.absUrl("href")
            val title = a.text().trim()
            if (href.startsWith("https://$host/") && trackPath.containsMatchIn(href) &&
                title.length in 3..180 && words.all { title.lowercase().contains(it) }
            ) Track(title, "", id, href) else null
        }.distinctBy { it.pageUrl }.take(30)
    }
}
