package com.voicechoicer.app.ui.projecthome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.db.ProjectEntity
import com.voicechoicer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProjectHomeUiState(
    val project: ProjectEntity? = null,
    val characterCount: Int = 0,
    val fragmentCount: Int = 0,
    val recordedCount: Int = 0,
    val playerCount: Int = 0,
) {
    val isReadyToExport: Boolean get() = fragmentCount > 0 && recordedCount > 0
}

@HiltViewModel
class ProjectHomeViewModel @Inject constructor(
    private val repository: ProjectRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = checkNotNull(savedStateHandle[Routes.ARG_PROJECT_ID])

    val state: StateFlow<ProjectHomeUiState> = combine(
        repository.observeProject(projectId),
        repository.observeCharacters(projectId),
        repository.observeFragments(projectId),
        repository.observeRecordedFragmentCount(projectId),
        repository.observePlayers(projectId),
    ) { project, characters, fragments, recordedCount, players ->
        ProjectHomeUiState(
            project = project,
            characterCount = characters.size,
            fragmentCount = fragments.size,
            recordedCount = recordedCount,
            playerCount = players.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectHomeUiState())
}
