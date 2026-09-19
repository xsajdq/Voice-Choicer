package com.voicechoicer.app.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eksport i podgląd") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("${state.recordedCount} / ${state.fragmentCount} fragmentów nagranych przez graczy")

            val dubbedPath = state.project?.dubbedVideoPath
            when {
                state.isExporting -> {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator()
                        Text("Budowanie filmu z waszym dubbingiem…")
                    }
                }
                dubbedPath == null -> {
                    Text(
                        "Gdy gotowi jesteście, zbuduj film łączący oryginalny obraz z waszymi nagranymi głosami.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = viewModel::startExport, enabled = state.canExport, modifier = Modifier.fillMaxWidth()) {
                        Text("Zbuduj film z dubbingiem")
                    }
                }
                else -> {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Oryginał") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Wasz dubbing") })
                    }
                    val path = if (selectedTab == 0) state.project?.localVideoPath else dubbedPath
                    path?.let { FullVideoPlayer(it, modifier = Modifier.fillMaxWidth()) }

                    Button(onClick = viewModel::startExport, modifier = Modifier.fillMaxWidth()) {
                        Text("Zbuduj ponownie (np. po nowych nagraniach)")
                    }
                }
            }
        }
    }
}
