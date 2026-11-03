package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.DownloadRight
import com.magral.muzykoisk.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Requires an official Jamendo developer client ID; do not embed a secret in a public repository. */
class JamendoSource(private val clientId: String) : MusicSource {
    override val id = "jamendo"
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext emptyList()
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=" +
            URLEncoder.encode(clientId, "UTF-8") +
            "&format=json&limit=30&search=" + URLEncoder.encode(query, "UTF-8")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 7000
        try {
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val results = json.getJSONArray("results")
            (0 until results.length()).map { i ->
                val obj = results.getJSONObject(i)
                val permitted = obj.optBoolean("audiodownload_allowed", false)
                Track(
                    title = obj.optString("name"),
                    artist = obj.optString("artist_name"),
                    sourceId = id,
                    pageUrl = obj.optString("shareurl", "https://www.jamendo.com/track/" + obj.optString("id")),
                    downloadUrl = if (permitted) obj.optString("audiodownload").takeIf { it.startsWith("https://") } else null,
                    right = if (permitted) DownloadRight.VERIFIED else DownloadRight.RESTRICTED,
                    durationSeconds = obj.optInt("duration")
                )
            }
        } finally { connection.disconnect() }
    }
}