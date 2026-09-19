package com.voicechoicer.app.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicechoicer.app.data.db.CharacterEntity
import com.voicechoicer.app.ui.common.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onBack: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val project by viewModel.project.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val players by viewModel.players.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val allCharacters = rows.mapNotNull { it.character }.distinctBy { it.id }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Nagrywanie kwestii") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { it.fragment.id }) { row ->
                FragmentCard(
                    row = row,
                    videoPath = project?.localVideoPath,
                    allCharacters = allCharacters,
                    onTextChange = { viewModel.updateFragmentText(row.fragment.id, it) },
                    onReassign = { viewModel.reassignCharacter(row.fragment.id, it) },
                    onRecordClick = { viewModel.openDialog(row) },
                )
            }
        }
    }

    val currentProject = project
    if (dialogState != null && currentProject != null) {
        RecordingSheet(
            state = dialogState!!,
            videoPath = currentProject.localVideoPath,
            players = players,
            elapsedMs = elapsedMs,
            amplitude = amplitude,
            onSelectPlayer = viewModel::selectPlayer,
            onStart = viewModel::startRecording,
            onStop = viewModel::stopRecording,
            onDiscard = viewModel::discardPending,
            onSave = viewModel::saveTake,
            onDismiss = viewModel::closeDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FragmentCard(
    row: FragmentRow,
    videoPath: String?,
    allCharacters: List<CharacterEntity>,
    onTextChange: (String) -> Unit,
    onReassign: (Long) -> Unit,
    onRecordClick: () -> Unit,
) {
    var text by remember(row.fragment.id, row.fragment.text) { mutableStateOf(row.fragment.text) }
    var showCharacterMenu by remember { mutableStateOf(false) }
    var showPreview by remember(row.fragment.id) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${formatDurationMs(row.fragment.startMs)} - ${formatDurationMs(row.fragment.endMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (row.hasSelectedTake) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Nagrano", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Brak nagrania")
                }
            }

            if (videoPath != null) {
                if (showPreview) {
                    VideoSnippetPlayer(
                        videoPath = videoPath,
                        startMs = row.fragment.startMs,
                        endMs = row.fragment.endMs,
                    )
                    TextButton(onClick = { showPreview = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ukryj podgląd")
                    }
                } else {
                    OutlinedButton(onClick = { showPreview = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(" Obejrzyj oryginalny fragment")
                    }
                }
            }

            Box {
                AssistChip(
                    onClick = { showCharacterMenu = true },
                    label = { Text(row.character?.name ?: "Postać") },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(row.character?.colorArgb ?: 0xFF888888.toInt())),
                        )
                    },
                )
                DropdownMenu(expanded = showCharacterMenu, onDismissRequest = { showCharacterMenu = false }) {
                    allCharacters.forEach { character ->
                        DropdownMenuItem(
                            text = { Text(character.name) },
                            onClick = { onReassign(character.id); showCharacterMenu = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; onTextChange(it) },
                label = { Text("Kwestia do przeczytania") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
            )

            Button(onClick = onRecordClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text(if (row.hasSelectedTake) "Nagraj ponownie" else "Nagraj")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingSheet(
    state: RecordingDialogState,
    videoPath: String,
    players: List<com.voicechoicer.app.data.db.PlayerEntity>,
    elapsedMs: Long,
    amplitude: Float,
    onSelectPlayer: (Long) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasRecordPermission = granted
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.fragment.character?.name ?: "Postać", style = MaterialTheme.typography.titleMedium)
            Text(
                state.fragment.fragment.text.ifBlank { "(brak tekstu — wpisz go na poprzednim ekranie)" },
                style = MaterialTheme.typography.headlineSmall,
            )

            VideoSnippetPlayer(
                videoPath = videoPath,
                startMs = state.fragment.fragment.startMs,
                endMs = state.fragment.fragment.endMs,
                muteWhileRecording = state.phase == RecordingPhase.RECORDING,
            )

            PlayerPicker(players = players, selectedPlayerId = state.selectedPlayerId, onSelectPlayer = onSelectPlayer)

            when (state.phase) {
                RecordingPhase.IDLE -> {
                    if (!hasRecordPermission) {
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Zezwól na dostęp do mikrofonu") }
                    } else {
                        Button(
                            onClick = onStart,
                            enabled = state.selectedPlayerId != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Text(" Nagraj swój głos")
                        }
                    }
                }
                RecordingPhase.RECORDING -> {
                    Text("Nagrywanie… ${formatDurationMs(elapsedMs)}", style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(progress = { amplitude }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text(" Zatrzymaj")
                    }
                }
                RecordingPhase.REVIEW -> {
                    Text("Nagranie: ${formatDurationMs(state.pendingDurationMs)}")
                    state.pendingFile?.let { file -> TakePreviewPlayer(file) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(" Nagraj ponownie")
                        }
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Zapisz") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPicker(
    players: List<com.voicechoicer.app.data.db.PlayerEntity>,
    selectedPlayerId: Long?,
    onSelectPlayer: (Long) -> Unit,
) {
    if (players.isEmpty()) {
        Text("Dodaj graczy na ekranie „Gracze”, aby móc nagrywać.", style = MaterialTheme.typography.bodySmall)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedName = players.firstOrNull { it.id == selectedPlayerId }?.name ?: "Wybierz gracza"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { player ->
                DropdownMenuItem(text = { Text(player.name) }, onClick = { onSelectPlayer(player.id); expanded = false })
            }
        }
    }
}

@Composable
private fun TakePreviewPlayer(file: java.io.File) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    androidx.compose.runtime.DisposableEffect(file.absolutePath) {
        onDispose { mediaPlayer?.release() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Odsłuchaj nagranie")
        }
        Text("Odsłuchaj swoje nagranie")
    }
}
