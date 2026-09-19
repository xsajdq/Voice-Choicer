package com.voicechoicer.app.ui.characters

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicechoicer.app.data.db.CharacterEntity
import com.voicechoicer.app.data.db.PlayerEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    onBack: () -> Unit,
    viewModel: CharactersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Postacie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Dodaj postać")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.characters, key = { it.id }) { character ->
                CharacterRow(
                    character = character,
                    players = state.players,
                    onRename = { viewModel.rename(character, it) },
                    onAssign = { viewModel.assignPlayer(character, it) },
                )
            }
        }
    }

    if (showAddDialog) {
        AddCharacterDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name -> viewModel.addCharacter(name); showAddDialog = false },
        )
    }
}

@Composable
private fun CharacterRow(
    character: CharacterEntity,
    players: List<PlayerEntity>,
    onRename: (String) -> Unit,
    onAssign: (Long?) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAssignMenu by remember { mutableStateOf(false) }
    val assignedPlayerName = players.firstOrNull { it.id == character.assignedPlayerId }?.name

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(character.colorArgb)),
            )
            Column(Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.titleMedium)
                Box {
                    TextButton(onClick = { showAssignMenu = true }) {
                        Text(assignedPlayerName ?: "Przypisz gracza")
                    }
                    DropdownMenu(expanded = showAssignMenu, onDismissRequest = { showAssignMenu = false }) {
                        DropdownMenuItem(text = { Text("Brak") }, onClick = { onAssign(null); showAssignMenu = false })
                        players.forEach { player ->
                            DropdownMenuItem(
                                text = { Text(player.name) },
                                onClick = { onAssign(player.id); showAssignMenu = false },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { showRenameDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Zmień nazwę")
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            initial = character.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { onRename(it); showRenameDialog = false },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zmień nazwę postaci") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun AddCharacterDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowa postać") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nazwa") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
