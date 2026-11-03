package com.magral.muzykoisk.sources

import com.magral.muzykoisk.domain.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

interface MusicSource {
    val id: String
    suspend fun search(query: String): List<Track>
}
class SearchEngine(private val sources: List<MusicSource>) {
    suspend fun search(query: String): List<Track> = supervisorScope {
        if (query.isBlank()) return@supervisorScope emptyList()
        sources.map { source ->
            async { runCatching { withTimeoutOrNull(20_000) { source.search(query.trim()) } ?: emptyList() }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().distinctBy { listOf(it.artist, it.title, it.sourceId).joinToString("|").lowercase() }
    }
}
