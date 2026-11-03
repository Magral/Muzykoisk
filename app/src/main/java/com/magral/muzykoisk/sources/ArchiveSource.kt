package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Search only. Archive records have varied rights; never infer download permission. */
class ArchiveSource : MusicSource {
    override val id = "archive"
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val expression = "mediatype:audio AND title:(${query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()})"
        val url = "https://archive.org/advancedsearch.php?q=" +
            URLEncoder.encode(expression, "UTF-8") +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&rows=20&page=1&output=json"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 7000
        try {
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val docs = json.getJSONObject("response").getJSONArray("docs")
            (0 until docs.length()).mapNotNull { i ->
                val obj = docs.getJSONObject(i)
                val identifier = obj.optString("identifier")
                if (identifier.isBlank()) null else Track(
                    title = obj.optString("title", identifier),
                    artist = obj.opt("creator")?.toString() ?: "",
                    sourceId = id,
                    pageUrl = "https://archive.org/details/" + URLEncoder.encode(identifier, "UTF-8")
                )
            }
        } finally { connection.disconnect() }
    }
}