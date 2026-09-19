package com.voicechoicer.app.ui.characters

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.db.CharacterEntity
import com.voicechoicer.app.data.db.PlayerEntity
import com.voicechoicer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CharactersUiState(
    val characters: List<CharacterEntity> = emptyList(),
    val players: List<PlayerEntity> = emptyList(),
)

@HiltViewModel
class CharactersViewModel @Inject constructor(
    private val repository: ProjectRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: Long = checkNotNull(savedStateHandle[Routes.ARG_PROJECT_ID])

    val state: StateFlow<CharactersUiState> = combine(
        repository.observeCharacters(projectId),
        repository.observePlayers(projectId),
    ) { characters, players -> CharactersUiState(characters, players) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CharactersUiState())

    fun rename(character: CharacterEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.renameCharacter(character, newName) }
    }

    fun assignPlayer(character: CharacterEntity, playerId: Long?) {
        viewModelScope.launch { repository.assignPlayerToCharacter(character.id, playerId) }
    }

    fun addCharacter(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addCharacter(projectId, name, state.value.characters.size) }
    }
}
