package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** Public site chart preview. These are links, not licensed downloadable files. */
class ChartSource {
    suspend fun weekly(limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect("https://nishevo.com/")
            .userAgent("Mozilla/5.0 (compatible; Muzykoisk/0.1)")
            .timeout(6500).maxBodySize(900_000).get()
        val heading = doc.select("h1,h2,h3").firstOrNull { it.text().contains("Топ-5 за неделю", true) }
            ?: return@withContext emptyList()
        val section = heading.parent() ?: return@withContext emptyList()
        section.select("a[href]").mapNotNull { a ->
            val url = a.absUrl("href")
            val title = a.text().trim()
            if (title.length < 3 || !url.startsWith("https://nishevo.com/")) null
            else Track(title = title, artist = "", sourceId = "nishevo-chart", pageUrl = url)
        }.distinctBy { it.pageUrl }.take(limit).toList()
    }
}
