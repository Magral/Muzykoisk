package com.magral.muzykoisk.domain

import org.junit.Assert.*
import org.junit.Test

class TrackTest {
    @Test fun unknownRightsNeverDownload() {
        val track = Track("Song", "Artist", "site", "https://example.org/song", "https://example.org/song.mp3")
        assertFalse(track.canDownload)
    }

    @Test fun verifiedHttpsMayDownload() {
        val track = Track("Song", "Artist", "site", "https://example.org/song",
            "https://example.org/song.mp3", DownloadRight.VERIFIED)
        assertTrue(track.canDownload)
    }

    @Test fun insecureUrlNeverDownload() {
        val track = Track("Song", "Artist", "site", "https://example.org/song",
            "http://example.org/song.mp3", DownloadRight.VERIFIED)
        assertFalse(track.canDownload)
    }
}
