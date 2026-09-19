package com.voicechoicer.app.ui.projecthome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicechoicer.app.ui.common.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHomeScreen(
    onBack: () -> Unit,
    onOpenCharacters: (Long) -> Unit,
    onOpenPlayers: (Long) -> Unit,
    onOpenRecording: (Long) -> Unit,
    onOpenExport: (Long) -> Unit,
    viewModel: ProjectHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val project = state.project

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            project?.let {
                Text("Długość: ${formatDurationMs(it.durationMs)}", style = MaterialTheme.typography.bodyMedium)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Postęp nagrywania", style = MaterialTheme.typography.titleSmall)
                    val progress = if (state.fragmentCount == 0) 0f else state.recordedCount.toFloat() / state.fragmentCount
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${state.recordedCount} / ${state.fragmentCount} fragmentów nagranych")
                }
            }

            NavigationRow(
                icon = Icons.Filled.People,
                title = "Postacie",
                subtitle = "${state.characterCount} wykrytych postaci — zmień nazwy i kolory",
                onClick = { onOpenCharacters(viewModel.projectId) },
            )
            NavigationRow(
                icon = Icons.Filled.Group,
                title = "Gracze",
                subtitle = "${state.playerCount} graczy — przypisz ich do postaci",
                onClick = { onOpenPlayers(viewModel.projectId) },
            )
            NavigationRow(
                icon = Icons.Filled.Mic,
                title = "Nagrywaj kwestie",
                subtitle = "Czytaj oryginalny tekst i nagraj swój głos",
                onClick = { onOpenRecording(viewModel.projectId) },
            )
            NavigationRow(
                icon = Icons.Filled.Movie,
                title = "Eksport i podgląd",
                subtitle = "Zbuduj film z waszym dubbingiem i porównaj z oryginałem",
                onClick = { onOpenExport(viewModel.projectId) },
                highlighted = state.isReadyToExport,
            )
        }
    }
}

@Composable
private fun NavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
