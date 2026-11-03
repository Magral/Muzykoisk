package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder

/**
 * Keyless public HTML search: discovers an actual GET search form rather than
 * inventing site-specific endpoints. Falls back to front-page discovery.
 * Does not bypass access restrictions and never claims download rights.
 */
class HtmlCatalogSource(
    override val id: String,
    private val homeUrl: String,
    private val trackPath: Regex
) : MusicSource {
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return@withContext emptyList()
        val home = fetch(homeUrl)
        val form = home.select("form").firstOrNull { element ->
            val method = element.attr("method").ifBlank { "get" }
            method.equals("get", ignoreCase = true) &&
                element.select("input[name]").any { input ->
                    val type = input.attr("type").lowercase()
                    type in listOf("", "search", "text") &&
                        input.attr("name").lowercase() in listOf("q", "s", "query", "search", "term", "keyword")
                }
        }
        val page = if (form == null) home else {
            val field = form.select("input[name]").first {
                it.attr("name").lowercase() in listOf("q", "s", "query", "search", "term", "keyword") &&
                    it.attr("type").lowercase() in listOf("", "search", "text")
            }
            val action = form.absUrl("action").ifBlank { homeUrl }
            val base = URI(homeUrl)
            val target = URI(action)
            if (target.scheme != "https" || target.host != base.host) home else {
                val params = form.select("input[name]").mapNotNull { input ->
                    val name = input.attr("name")
                    if (name == field.attr("name")) name to query
                    else if (input.attr("type").lowercase() == "hidden") name to input.attr("value")
                    else null
                }
                val encoded = params.joinToString("&") { (k,v) ->
                    URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
                }
                val separator = if (action.contains("?")) "&" else "?"
                fetch(action + separator + encoded)
            }
        }
        val host = URI(homeUrl).host
        page.select("a[href]").asSequence().mapNotNull { link ->
            val url = link.absUrl("href")
            val title = link.text().trim()
            val parsed = runCatching { URI(url) }.getOrNull()
            if (parsed?.scheme != "https" || parsed.host != host ||
                !trackPath.containsMatchIn(parsed.path ?: "") ||
                title.length !in 3..160 || !terms.all { title.lowercase().contains(it) }
            ) null else Track(title = title, artist = "", sourceId = id, pageUrl = url)
        }.distinctBy { it.pageUrl }.take(30).toList()
    }

    private fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (compatible; Muzykoisk/0.1)")
        .timeout(7000).maxBodySize(1_000_000).get()
}
