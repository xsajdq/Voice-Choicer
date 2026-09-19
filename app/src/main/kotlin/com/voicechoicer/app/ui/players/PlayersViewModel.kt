package com.voicechoicer.app.ui.players

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.db.PlayerEntity
import com.voicechoicer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayersViewModel @Inject constructor(
    private val repository: ProjectRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: Long = checkNotNull(savedStateHandle[Routes.ARG_PROJECT_ID])

    val players: StateFlow<List<PlayerEntity>> = repository.observePlayers(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPlayer(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addPlayer(projectId, name) }
    }

    fun removePlayer(player: PlayerEntity) {
        viewModelScope.launch { repository.removePlayer(player) }
    }
}
