package com.voicechoicer.app.ui.recording

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.db.CharacterEntity
import com.voicechoicer.app.data.db.FragmentEntity
import com.voicechoicer.app.data.db.PlayerEntity
import com.voicechoicer.app.data.db.ProjectEntity
import com.voicechoicer.app.data.db.TakeEntity
import com.voicechoicer.app.media.TakeRecorder
import com.voicechoicer.app.ui.navigation.Routes
import com.voicechoicer.core.audio.Wav
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FragmentRow(
    val fragment: FragmentEntity,
    val character: CharacterEntity?,
    val hasSelectedTake: Boolean,
)

enum class RecordingPhase { IDLE, RECORDING, REVIEW }

data class RecordingDialogState(
    val fragment: FragmentRow,
    val selectedPlayerId: Long?,
    val phase: RecordingPhase,
    val pendingFile: File? = null,
    val pendingDurationMs: Long = 0L,
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val takeRecorder: TakeRecorder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = checkNotNull(savedStateHandle[Routes.ARG_PROJECT_ID])

    val project: StateFlow<ProjectEntity?> = repository.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val players: StateFlow<List<PlayerEntity>> = repository.observePlayers(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rows: StateFlow<List<FragmentRow>> = combine(
        repository.observeFragments(projectId),
        repository.observeCharacters(projectId),
        repository.observeRecordedFragmentCount(projectId), // re-triggers when any take selection changes
    ) { fragments, characters, _ -> fragments to characters }
        .flatMapLatest { (fragments, characters) ->
            kotlinx.coroutines.flow.flow {
                val charactersById = characters.associateBy { it.id }
                val selected = fragments.associate { it.id to repository.getSelectedTake(it.id) }
                emit(
                    fragments.map { fragment ->
                        FragmentRow(
                            fragment = fragment,
                            character = charactersById[fragment.characterId],
                            hasSelectedTake = selected[fragment.id] != null,
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val elapsedMs: StateFlow<Long> = takeRecorder.elapsedMs
    val amplitude: StateFlow<Float> = takeRecorder.amplitude

    private val _dialogState = MutableStateFlow<RecordingDialogState?>(null)
    val dialogState: StateFlow<RecordingDialogState?> = _dialogState

    fun updateFragmentText(fragmentId: Long, text: String) {
        viewModelScope.launch { repository.updateFragmentText(fragmentId, text) }
    }

    fun reassignCharacter(fragmentId: Long, characterId: Long) {
        viewModelScope.launch { repository.reassignFragmentCharacter(fragmentId, characterId) }
    }

    fun openDialog(row: FragmentRow) {
        _dialogState.value = RecordingDialogState(
            fragment = row,
            selectedPlayerId = row.character?.assignedPlayerId ?: players.value.firstOrNull()?.id,
            phase = RecordingPhase.IDLE,
        )
    }

    fun selectPlayer(playerId: Long) {
        _dialogState.value = _dialogState.value?.copy(selectedPlayerId = playerId)
    }

    fun startRecording() {
        val current = _dialogState.value ?: return
        takeRecorder.start()
        _dialogState.value = current.copy(phase = RecordingPhase.RECORDING)
    }

    fun stopRecording() {
        val current = _dialogState.value ?: return
        val recorded = takeRecorder.stop()
        val file = repository.newTakeFile(current.fragment.fragment.id)
        file.writeBytes(Wav.encode(recorded.pcm, recorded.sampleRate))
        val durationMs = if (recorded.sampleRate > 0) recorded.pcm.size.toLong() * 1000 / recorded.sampleRate else 0
        _dialogState.value = current.copy(phase = RecordingPhase.REVIEW, pendingFile = file, pendingDurationMs = durationMs)
    }

    fun discardPending() {
        val current = _dialogState.value ?: return
        current.pendingFile?.delete()
        _dialogState.value = current.copy(phase = RecordingPhase.IDLE, pendingFile = null, pendingDurationMs = 0L)
    }

    fun saveTake() {
        val current = _dialogState.value ?: return
        val playerId = current.selectedPlayerId ?: return
        val file = current.pendingFile ?: return
        viewModelScope.launch {
            repository.saveTake(current.fragment.fragment.id, playerId, file, current.pendingDurationMs)
            _dialogState.value = null
        }
    }

    fun closeDialog() {
        if (takeRecorder.isRecording) takeRecorder.cancel()
        _dialogState.value?.pendingFile?.let { if (it.exists()) it.delete() }
        _dialogState.value = null
    }
}
