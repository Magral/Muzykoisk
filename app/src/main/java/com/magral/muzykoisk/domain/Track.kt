package com.magral.muzykoisk.domain

enum class DownloadRight { VERIFIED, UNKNOWN, RESTRICTED }
data class Track(
    val title: String,
    val artist: String,
    val sourceId: String,
    val pageUrl: String,
    val downloadUrl: String? = null,
    val right: DownloadRight = DownloadRight.UNKNOWN,
    val durationSeconds: Int? = null,
    val license: String? = null
) {
    val canDownload: Boolean get() = right == DownloadRight.VERIFIED && downloadUrl?.startsWith("https://") == true
}