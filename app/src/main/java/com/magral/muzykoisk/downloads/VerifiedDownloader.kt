package com.magral.muzykoisk.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.magral.muzykoisk.domain.Track

/**
 * Android OS handles downloads. Call only for tracks whose download rights
 * have been independently verified by a source adapter.
 */
class VerifiedDownloader(private val context: Context) {
    fun enqueue(track: Track): Long {
        require(track.canDownload) { "Download rights not verified" }
        val url = requireNotNull(track.downloadUrl)
        require(Uri.parse(url).scheme == "https")
        require(Uri.parse(url).host != null) { "Invalid download host" }
        val filename = (track.artist + " - " + track.title)
            .replace(Regex("[^\\p{L}\\p{N} ._-]"), "_")
            .take(110).trim().ifEmpty { "audio" } + ".mp3"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.title)
            .setMimeType("audio/mpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Muzykoisk/$filename")
            .setAllowedOverMetered(true)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
