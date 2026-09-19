package com.voicechoicer.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicechoicer.app.data.db.ProjectEntity
import com.voicechoicer.app.ui.common.formatDate
import com.voicechoicer.app.ui.common.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onOpenProject: (Long) -> Unit,
    onImportNew: () -> Unit,
    viewModel: ProjectListViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Voice Choicer") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onImportNew) {
                Icon(Icons.Filled.Add, contentDescription = "Dodaj fragment filmu")
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            EmptyState(padding, onImportNew)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onOpenProject(project.id) },
                        onDelete = { viewModel.deleteProject(project) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues, onImportNew: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.MovieCreation,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text("Brak projektów jeszcze.")
            Text("Dodaj fragment filmu, aby zacząć dubbingować z ekipą!")
            androidx.compose.material3.TextButton(onClick = onImportNew) {
                Text("Dodaj pierwszy fragment")
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(project.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(
                    "${formatDurationMs(project.durationMs)} • ${formatDate(project.createdAt)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                if (project.dubbedVideoPath != null) {
                    Text(
                        "Dubbing gotowy ✓",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Usuń projekt")
            }
        }
    }
}
