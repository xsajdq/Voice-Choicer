package com.voicechoicer.app.ui.export

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.db.ProjectEntity
import com.voicechoicer.app.export.ExportService
import com.voicechoicer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExportUiState(
    val project: ProjectEntity? = null,
    val fragmentCount: Int = 0,
    val recordedCount: Int = 0,
    val isExporting: Boolean = false,
) {
    val canExport: Boolean get() = recordedCount > 0 && !isExporting
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: ProjectRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: Long = checkNotNull(savedStateHandle[Routes.ARG_PROJECT_ID])

    val state: StateFlow<ExportUiState> = combine(
        repository.observeProject(projectId),
        repository.observeFragments(projectId),
        repository.observeRecordedFragmentCount(projectId),
    ) { project, fragments, recordedCount ->
        ExportUiState(
            project = project,
            fragmentCount = fragments.size,
            recordedCount = recordedCount,
            isExporting = project != null && project.dubbedVideoPath == null && exportRequested,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExportUiState())

    @Volatile private var exportRequested = false

    fun startExport() {
        exportRequested = true
        ExportService.start(context, projectId)
    }
}
