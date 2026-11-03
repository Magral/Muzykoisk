package com.magral.muzykoisk

import android.content.Intent
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.magral.muzykoisk.domain.Track
import com.magral.muzykoisk.sources.ArchiveSource
import com.magral.muzykoisk.sources.JamendoSource
import com.magral.muzykoisk.sources.SearchEngine
import com.magral.muzykoisk.sources.SourceRegistry
import com.magral.muzykoisk.sources.ChartSource
import com.magral.muzykoisk.downloads.VerifiedDownloader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MuzykoiskApp() } }
    }
}

@Composable
fun MuzykoiskApp() {
    val tabs = listOf("Поиск", "Чарты", "Загрузки", "Моя музыка", "Настройки")
    var selected by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var chart by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("muzykoisk_library", Context.MODE_PRIVATE) }
    var savedTracks by remember { mutableStateOf(readSavedTracks(prefs.getString("tracks", "[]") ?: "[]")) }
    fun toggleSaved(track: Track) {
        savedTracks = if (savedTracks.any { it.pageUrl == track.pageUrl }) savedTracks.filterNot { it.pageUrl == track.pageUrl } else savedTracks + track
        prefs.edit().putString("tracks", encodeSavedTracks(savedTracks)).apply()
    }
    // Jamendo requires a developer client ID; empty until configured.
    val engine = remember { SearchEngine(SourceRegistry.keyless() + ArchiveSource()) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    Scaffold(bottomBar = {
        ScrollableTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = selected == i, onClick = { selected = i }, text = { Text(title) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Музыкоиск", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            if (selected == 0) {
                OutlinedTextField(query, { query = it }, label = { Text("Трек или исполнитель") }, modifier = Modifier.fillMaxWidth())
                Row {
                    Button(enabled = query.isNotBlank() && !searching, onClick = {
                        searchJob?.cancel()
                        searchJob = scope.launch {
                            searching = true
                            message = ""
                            try {
                                results = engine.search(query)
                                if (results.isEmpty()) message = "Ничего не найдено"
                            } catch (_: kotlinx.coroutines.CancellationException) {
                                message = "Поиск отменён"
                            } finally { searching = false }
                        }
                    }) { Text("Найти") }
                    Spacer(Modifier.width(8.dp))
                    if (searching) OutlinedButton(onClick = { searchJob?.cancel() }) { Text("Отмена") }
                }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message.isNotBlank()) Text(message)
                LazyColumn {
                    items(results) { track ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium)
                            Text("${track.artist} · ${track.sourceId}")
                            Text(if (track.canDownload) "Загрузка разрешена" else "Открыть страницу источника")
                            Row {
                                TextButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.pageUrl)))
                                }) { Text("Источник") }
                                TextButton(onClick = { toggleSaved(track) }) {
                                    Text(if (savedTracks.any { it.pageUrl == track.pageUrl }) "Убрать из избранного" else "В избранное")
                                }
                                if (track.canDownload) TextButton(onClick = {
                                    runCatching { VerifiedDownloader(context).enqueue(track) }
                                        .onFailure { message = "Ошибка загрузки: " + it.message }
                                }) { Text("Скачать") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            } else if (selected == 1) {
                Button(onClick = {
                    scope.launch {
                        searching = true
                        try { chart = ChartSource().weekly(); message = if (chart.isEmpty()) "Чарт недоступен" else "" }
                        catch (e: Exception) { message = "Ошибка загрузки чарта" }
                        finally { searching = false }
                    }
                }, enabled = !searching) { Text("Загрузить топ недели") }
                if (message.isNotBlank()) Text(message)
                LazyColumn { items(chart) { track ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.pageUrl))) }) {
                        Text(track.title)
                    }
                } }
            } else if (selected == 3) {
                Text("Избранное: ${savedTracks.size}", style = MaterialTheme.typography.titleMedium)
                if (savedTracks.isEmpty()) Text("Добавляйте треки из результатов поиска.")
                LazyColumn {
                    items(savedTracks, key = { it.pageUrl }) { track ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium)
                            Text("${track.artist} · ${track.sourceId}")
                            Row {
                                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.pageUrl))) }) { Text("Источник") }
                                TextButton(onClick = { toggleSaved(track) }) { Text("Удалить") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            } else {
                Text("${tabs[selected]} — раздел в разработке")
            }
        }
    }
}

private fun encodeSavedTracks(tracks: List<Track>): String {
    val array = JSONArray()
    tracks.forEach { track ->
        array.put(JSONObject().apply {
            put("title", track.title)
            put("artist", track.artist)
            put("sourceId", track.sourceId)
            put("pageUrl", track.pageUrl)
        })
    }
    return array.toString()
}

private fun readSavedTracks(json: String): List<Track> = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val url = item.optString("pageUrl")
        if (!url.startsWith("https://")) return@mapNotNull null
        Track(title = item.optString("title"), artist = item.optString("artist"), sourceId = item.optString("sourceId"), pageUrl = url)
    }
}.getOrDefault(emptyList())
