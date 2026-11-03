package com.magral.muzykoisk

import android.content.Intent
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
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Jamendo requires a developer client ID; empty until configured.
    val engine = remember { SearchEngine(listOf(ArchiveSource(), JamendoSource(""))) }
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
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.pageUrl)))
                            }) { Text("Источник") }
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
