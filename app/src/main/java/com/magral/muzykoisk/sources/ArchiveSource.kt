package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.Track
import com.magral.muzykoisk.domain.DownloadRight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Search returns only records with an available MP3 and a supported permission marker. */
class ArchiveSource : MusicSource {
    override val id = "archive"
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val expression = "mediatype:audio AND title:(${query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()})"
        val url = "https://archive.org/advancedsearch.php?q=" +
            URLEncoder.encode(expression, "UTF-8") +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&rows=12&page=1&output=json"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 7000
        try {
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val docs = json.getJSONObject("response").getJSONArray("docs")
            val candidates = (0 until docs.length()).mapNotNull { i ->
                val obj = docs.getJSONObject(i)
                val identifier = obj.optString("identifier")
                if (identifier.isBlank()) null else Track(
                    title = obj.optString("title", identifier),
                    artist = obj.opt("creator")?.toString() ?: "",
                    sourceId = id,
                    pageUrl = "https://archive.org/details/" + URLEncoder.encode(identifier, "UTF-8")
                )
            }
            coroutineScope {
                val limiter = Semaphore(3)
                candidates.take(6).map { candidate ->
                    async {
                        limiter.withPermit {
                            runCatching { resolveDownload(candidate) }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } finally { connection.disconnect() }
    }

    /**
     * Resolves an Internet Archive item only when its metadata declares a
     * recognized public-domain or permissive Creative Commons license and
     * contains a direct MP3 file. Unknown or restrictive rights stay blocked.
     */
    suspend fun resolveDownload(track: Track): Track? = withContext(Dispatchers.IO) {
        if (track.sourceId != id) return@withContext null
        val page = runCatching { URI(track.pageUrl) }.getOrNull() ?: return@withContext null
        if (page.scheme != "https" || page.host !in setOf("archive.org", "www.archive.org")) return@withContext null
        val identifier = page.path.orEmpty().removePrefix("/details/").substringBefore('/').takeIf {
            page.path.orEmpty().startsWith("/details/") && it.matches(Regex("[A-Za-z0-9._-]{1,160}"))
        } ?: return@withContext null
        val encodedId = URLEncoder.encode(identifier, "UTF-8")
        val connection = URL("https://archive.org/metadata/$encodedId").openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 7000
        try {
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val metadata = response.optJSONObject("metadata") ?: return@withContext null
            val license = metadata.optString("licenseurl").trim().trimEnd('/')
                .replaceFirst("http://", "https://")
                .lowercase()
            if (license !in APPROVED_LICENSES) return@withContext null
            val files = response.optJSONArray("files") ?: return@withContext null
            val file = (0 until files.length()).asSequence()
                .mapNotNull { files.optJSONObject(it) }
                .filter { it.optString("name").lowercase().endsWith(".mp3") }
                .filterNot { it.optString("private").equals("true", ignoreCase = true) }
                .filter { it.optLong("size", 0L) > 0L }
                .minByOrNull { it.optLong("size", Long.MAX_VALUE) }
                ?: return@withContext null
            val fileName = file.optString("name")
            if (fileName.isBlank() || fileName.split('/').any { it == ".." }) return@withContext null
            val encodedName = fileName.split('/').joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val downloadUrl = "https://archive.org/download/$encodedId/$encodedName"
            track.copy(downloadUrl = downloadUrl, right = DownloadRight.VERIFIED, license = license)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val APPROVED_LICENSES = setOf(
            "https://creativecommons.org/publicdomain/zero/1.0",
            "https://creativecommons.org/publicdomain/mark/1.0",
            "https://creativecommons.org/licenses/by/3.0",
            "https://creativecommons.org/licenses/by/4.0",
            "https://creativecommons.org/licenses/by-sa/3.0",
            "https://creativecommons.org/licenses/by-sa/4.0"
        )
    }
}
