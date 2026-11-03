package com.magral.muzykoisk

import android.app.DownloadManager
import android.content.Context
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
import com.magral.muzykoisk.domain.DownloadRight
import com.magral.muzykoisk.domain.Track
import com.magral.muzykoisk.downloads.DownloadHistory
import com.magral.muzykoisk.downloads.DownloadSnapshot
import com.magral.muzykoisk.downloads.VerifiedDownloader
import com.magral.muzykoisk.sources.ArchiveSource
import com.magral.muzykoisk.sources.ChartSource
import com.magral.muzykoisk.sources.SourceRegistry
import com.magral.muzykoisk.sources.SearchEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
    var downloads by remember { mutableStateOf<List<DownloadSnapshot>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var preparingDownload by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("muzykoisk_library", Context.MODE_PRIVATE) }
    val downloadHistory = remember(context) { DownloadHistory(context) }
    var savedTracks by remember { mutableStateOf(readSavedTracks(prefs.getString("tracks", "[]") ?: "[]")) }

    fun toggleSaved(track: Track) {
        savedTracks = if (savedTracks.any { it.pageUrl == track.pageUrl }) {
            savedTracks.filterNot { it.pageUrl == track.pageUrl }
        } else savedTracks + track
        prefs.edit().putString("tracks", encodeSavedTracks(savedTracks)).apply()
    }

    fun startDownload(track: Track) {
        scope.launch {
            preparingDownload = track.pageUrl
            message = ""
            try {
                val downloadable = when {
                    track.canDownload -> track
                    track.sourceId == "archive" -> ArchiveSource().resolveDownload(track)
                    else -> null
                }
                if (downloadable == null) {
                    message = "Источник не подтвердил разрешённую прямую MP3-загрузку."
                } else {
                    if (savedTracks.any { it.pageUrl == track.pageUrl }) {
                        savedTracks = savedTracks.map { if (it.pageUrl == track.pageUrl) downloadable else it }
                        prefs.edit().putString("tracks", encodeSavedTracks(savedTracks)).apply()
                    }
                    val id = VerifiedDownloader(context).enqueue(downloadable)
                    downloadHistory.add(id, downloadable)
                    selected = 2
                    message = "Загрузка началась. Её состояние показано во вкладке «Загрузки»."
                }
            } catch (e: Exception) {
                message = "Не удалось начать загрузку: ${e.message ?: "ошибка сети"}"
            } finally {
                preparingDownload = null
            }
        }
    }

    val engine = remember { SearchEngine(SourceRegistry.keyless() + ArchiveSource()) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(selected) {
        while (selected == 2) {
            downloads = downloadHistory.snapshots()
            delay(900)
        }
    }

    Scaffold(bottomBar = {
        ScrollableTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Музыкоиск", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            when (selected) {
                0 -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Трек или исполнитель") },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                } finally {
                                    searching = false
                                }
                            }
                        }) { Text("Найти") }
                        Spacer(Modifier.width(8.dp))
                        if (searching) OutlinedButton(onClick = { searchJob?.cancel() }) { Text("Отмена") }
                    }
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 8.dp))
                    LazyColumn {
                        items(results, key = { it.pageUrl }) { track ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(track.title, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(track.artist, track.sourceId).filter { it.isNotBlank() }.joinToString(" · "))
                                Text(downloadDescription(track))
                                Row {
                                    TextButton(onClick = { toggleSaved(track) }) {
                                        Text(if (savedTracks.any { it.pageUrl == track.pageUrl }) "Убрать из избранного" else "В избранное")
                                    }
                                    if (track.canDownload || track.sourceId == "archive") {
                                        TextButton(
                                            enabled = preparingDownload != track.pageUrl,
                                            onClick = { startDownload(track) }
                                        ) {
                                            Text(if (preparingDownload == track.pageUrl) "Проверка…" else downloadButtonText(track))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                1 -> {
                    Button(onClick = {
                        scope.launch {
                            searching = true
                            message = ""
                            try {
                                chart = ChartSource().weekly()
                                if (chart.isEmpty()) message = "Чарт недоступен"
                            } catch (_: Exception) {
                                message = "Ошибка загрузки чарта"
                            } finally {
                                searching = false
                            }
                        }
                    }, enabled = !searching) { Text("Загрузить топ недели") }
                    if (message.isNotBlank()) Text(message)
                    LazyColumn {
                        items(chart, key = { it.pageUrl }) { track ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(track.title, style = MaterialTheme.typography.titleMedium)
                                if (track.canDownload || track.sourceId == "archive") {
                                    TextButton(onClick = { startDownload(track) }) { Text(downloadButtonText(track)) }
                                } else Text(downloadDescription(track))
                            }
                            HorizontalDivider()
                        }
                    }
                }
                2 -> {
                    Text("Загрузки: ${downloads.size}", style = MaterialTheme.typography.titleMedium)
                    if (downloads.isEmpty()) Text("Здесь появятся загрузки, начатые в приложении.")
                    LazyColumn {
                        items(downloads, key = { it.record.id }) { item ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(item.record.track.title, style = MaterialTheme.typography.titleMedium)
                                Text(downloadStatus(item))
                                if (item.totalBytes > 0 && item.status == DownloadManager.STATUS_RUNNING) {
                                    val progress = (item.downloadedBytes.toFloat() / item.totalBytes).coerceIn(0f, 1f)
                                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                                    Text("${(progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)} из ${formatBytes(item.totalBytes)}")
                                }
                                item.record.track.license?.let { Text("Лицензия: $it", style = MaterialTheme.typography.bodySmall) }
                                TextButton(onClick = { downloadHistory.remove(item.record.id); downloads = downloadHistory.snapshots() }) {
                                    Text(if (item.status == DownloadManager.STATUS_SUCCESSFUL) "Удалить файл" else "Убрать из списка")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                3 -> {
                    Text("Избранное: ${savedTracks.size}", style = MaterialTheme.typography.titleMedium)
                    if (savedTracks.isEmpty()) Text("Добавляйте треки из результатов поиска.")
                    LazyColumn {
                        items(savedTracks, key = { it.pageUrl }) { track ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(track.title, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(track.artist, track.sourceId).filter { it.isNotBlank() }.joinToString(" · "))
                                Text(downloadDescription(track))
                                Row {
                                    if (track.canDownload || track.sourceId == "archive") {
                                        TextButton(
                                            enabled = preparingDownload != track.pageUrl,
                                            onClick = { startDownload(track) }
                                        ) { Text(if (preparingDownload == track.pageUrl) "Проверка…" else downloadButtonText(track)) }
                                    }
                                    TextButton(onClick = { toggleSaved(track) }) { Text("Удалить") }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                else -> Text("${tabs[selected]} — раздел в разработке")
            }
        }
    }
}

private fun downloadButtonText(track: Track) =
    if (track.canDownload) "Скачать" else "Проверить и скачать"

private fun downloadDescription(track: Track) = when {
    track.canDownload -> "Разрешённая загрузка подтверждена"
    track.sourceId == "archive" -> "Проверить лицензию и скачать MP3 в приложении"
    else -> "Прямая загрузка недоступна: права и MP3-ссылка не подтверждены"
}

private fun downloadStatus(item: DownloadSnapshot): String = when (item.status) {
    DownloadManager.STATUS_PENDING -> "В очереди"
    DownloadManager.STATUS_RUNNING -> "Скачивается · ${formatBytes(item.downloadedBytes)}"
    DownloadManager.STATUS_PAUSED -> "Приостановлено"
    DownloadManager.STATUS_SUCCESSFUL -> "Скачано в Музыку/Muzykoisk"
    DownloadManager.STATUS_FAILED -> "Ошибка загрузки (${item.reason})"
    else -> "Запись загрузки больше недоступна"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f МБ".format(bytes / 1_048_576f)
    bytes >= 1_024 -> "%.0f КБ".format(bytes / 1_024f)
    else -> "$bytes Б"
}

private fun encodeSavedTracks(tracks: List<Track>): String {
    val array = JSONArray()
    tracks.forEach { track ->
        array.put(JSONObject().apply {
            put("title", track.title)
            put("artist", track.artist)
            put("sourceId", track.sourceId)
            put("pageUrl", track.pageUrl)
            put("downloadUrl", track.downloadUrl)
            put("right", track.right.name)
            put("durationSeconds", track.durationSeconds)
            put("license", track.license)
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
        val downloadUrl = item.optString("downloadUrl").takeIf { it.startsWith("https://") }
        val right = runCatching { DownloadRight.valueOf(item.optString("right", "UNKNOWN")) }
            .getOrDefault(DownloadRight.UNKNOWN)
        Track(
            title = item.optString("title"),
            artist = item.optString("artist"),
            sourceId = item.optString("sourceId"),
            pageUrl = url,
            downloadUrl = downloadUrl,
            right = right,
            durationSeconds = item.optInt("durationSeconds").takeIf { it > 0 },
            license = item.optString("license").takeIf { it.isNotBlank() }
        )
    }
}.getOrDefault(emptyList())
