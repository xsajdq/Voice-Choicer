package com.voicechoicer.app.ui.importflow

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicechoicer.app.importer.ImportProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImported: (Long) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri, queryDisplayName(uri.toString(), context))
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onSubtitlePicked(uri, queryDisplayName(uri.toString(), context))
    }

    LaunchedEffect(state.progress) {
        val progress = state.progress
        if (progress is ImportProgress.Done && progress.warning == null) onImported(progress.projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nowy fragment") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isImporting) {
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
            TextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Nazwa projektu") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isImporting,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Plik wideo *")
                Button(
                    onClick = { videoPicker.launch(arrayOf("video/*")) },
                    enabled = !state.isImporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(state.videoFileName ?: "Wybierz wideo z urządzenia")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Plik napisów .srt/.vtt (opcjonalnie)")
                Text(
                    "Jeśli dodasz napisy, aplikacja precyzyjnie podzieli film na kwestie i rozpozna " +
                        "postacie po formacie „IMIĘ: tekst” lub dialogach z myślnikiem. Bez napisów film " +
                        "zostanie podzielony automatycznie na podstawie ciszy, a tekst trzeba będzie wpisać ręcznie.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                if (state.subtitleFileName != null) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(state.subtitleFileName!!, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::clearSubtitle, enabled = !state.isImporting) {
                            Icon(Icons.Filled.Close, contentDescription = "Usuń napisy")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { subtitlePicker.launch(arrayOf("*/*")) },
                        enabled = !state.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Wybierz plik napisów")
                    }
                }
            }

            if (state.isImporting) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Text(progressLabel(state.progress))
                }
            }

            state.error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

            val doneWithWarning = (state.progress as? ImportProgress.Done)?.warning
            if (doneWithWarning != null) {
                androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(doneWithWarning, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { onImported((state.progress as ImportProgress.Done).projectId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Przejdź do projektu")
                        }
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.startImport() },
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Importuj i podziel na fragmenty")
                }
            }
        }
    }
}

private fun progressLabel(progress: ImportProgress?): String = when (progress) {
    is ImportProgress.CopyingVideo -> "Kopiowanie wideo…"
    is ImportProgress.ReadingSubtitles -> "Wczytywanie napisów…"
    is ImportProgress.AnalyzingAudio -> "Analiza dźwięku…"
    is ImportProgress.TranscribingSpeech -> "Rozpoznawanie mowy (może chwilę potrwać)…"
    is ImportProgress.DetectingSpeakers -> "Rozpoznawanie rozmówców…"
    is ImportProgress.Saving -> "Zapisywanie projektu…"
    is ImportProgress.Done, null -> ""
    is ImportProgress.Failed -> "Błąd: ${progress.message}"
}

private fun queryDisplayName(uriString: String, context: android.content.Context): String? {
    val uri = android.net.Uri.parse(uriString)
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}
