package com.magral.muzykoisk.downloads

import android.app.DownloadManager
import android.content.Context
import com.magral.muzykoisk.domain.DownloadRight
import com.magral.muzykoisk.domain.Track
import org.json.JSONArray
import org.json.JSONObject

data class DownloadRecord(val id: Long, val track: Track)

data class DownloadSnapshot(
    val record: DownloadRecord,
    val status: Int,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val reason: Int = 0
)

/** Keeps the in-app download list while Android DownloadManager transfers the file. */
class DownloadHistory(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("muzykoisk_downloads", Context.MODE_PRIVATE)

    fun add(id: Long, track: Track) {
        val records = records().filterNot { it.id == id } + DownloadRecord(id, track)
        save(records)
    }

    fun remove(id: Long) {
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.remove(id)
        save(records().filterNot { it.id == id })
    }

    fun snapshots(): List<DownloadSnapshot> {
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return records().map { record ->
            val cursor = manager.query(DownloadManager.Query().setFilterById(record.id))
            cursor.use {
                if (it == null || !it.moveToFirst()) return@map DownloadSnapshot(record, -1)
                DownloadSnapshot(
                    record = record,
                    status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    downloadedBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                )
            }
        }.reversed()
    }

    private fun records(): List<DownloadRecord> = runCatching {
        val array = JSONArray(preferences.getString("items", "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("id", -1L)
            val track = item.optJSONObject("track")?.let(::decodeTrack)
            if (id < 0 || track == null) null else DownloadRecord(id, track)
        }
    }.getOrDefault(emptyList())

    private fun save(records: List<DownloadRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().put("id", record.id).put("track", encodeTrack(record.track)))
        }
        preferences.edit().putString("items", array.toString()).apply()
    }

    private fun encodeTrack(track: Track) = JSONObject().apply {
        put("title", track.title)
        put("artist", track.artist)
        put("sourceId", track.sourceId)
        put("pageUrl", track.pageUrl)
        put("downloadUrl", track.downloadUrl)
        put("right", track.right.name)
        put("license", track.license)
    }

    private fun decodeTrack(item: JSONObject): Track? {
        val pageUrl = item.optString("pageUrl")
        if (!pageUrl.startsWith("https://")) return null
        val downloadUrl = item.optString("downloadUrl").takeIf { it.startsWith("https://") }
        val right = runCatching { DownloadRight.valueOf(item.optString("right", "UNKNOWN")) }
            .getOrDefault(DownloadRight.UNKNOWN)
        return Track(
            title = item.optString("title"),
            artist = item.optString("artist"),
            sourceId = item.optString("sourceId"),
            pageUrl = pageUrl,
            downloadUrl = downloadUrl,
            right = right,
            license = item.optString("license").takeIf { it.isNotBlank() }
        )
    }
}
