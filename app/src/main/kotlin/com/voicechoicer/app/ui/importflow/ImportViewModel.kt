package com.voicechoicer.app.ui.importflow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechoicer.app.importer.ImportPipeline
import com.voicechoicer.app.importer.ImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val title: String = "",
    val videoUri: Uri? = null,
    val videoFileName: String? = null,
    val subtitleUri: Uri? = null,
    val subtitleFileName: String? = null,
    val progress: ImportProgress? = null,
    val error: String? = null,
) {
    val isImporting: Boolean get() = progress != null && progress !is ImportProgress.Done && progress !is ImportProgress.Failed
    val canStart: Boolean get() = videoUri != null && title.isNotBlank() && !isImporting
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importPipeline: ImportPipeline,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state

    fun onTitleChange(title: String) = _state.update { it.copy(title = title) }

    fun onVideoPicked(uri: Uri?, fileName: String?) = _state.update {
        it.copy(
            videoUri = uri,
            videoFileName = fileName,
            title = it.title.ifBlank { fileName?.substringBeforeLast('.') ?: it.title },
        )
    }

    fun onSubtitlePicked(uri: Uri?, fileName: String?) = _state.update {
        it.copy(subtitleUri = uri, subtitleFileName = fileName)
    }

    fun clearSubtitle() = _state.update { it.copy(subtitleUri = null, subtitleFileName = null) }

    fun startImport(onImported: (Long) -> Unit) {
        val current = _state.value
        val videoUri = current.videoUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                val projectId = importPipeline.import(
                    title = current.title.ifBlank { "Bez nazwy" },
                    videoUri = videoUri,
                    subtitleUri = current.subtitleUri,
                ) { progress -> _state.update { it.copy(progress = progress) } }
                onImported(projectId)
            } catch (t: Throwable) {
                _state.update { it.copy(progress = ImportProgress.Failed(t.message ?: "Nieznany błąd"), error = t.message) }
            }
        }
    }
}
