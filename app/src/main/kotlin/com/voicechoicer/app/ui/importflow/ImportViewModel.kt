package com.voicechoicer.app.ui.importflow

import android.net.Uri
import android.util.Log
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
    val pastedTranscript: String = "",
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
        // A picked file and pasted text are alternative sources for the same thing - only one
        // can win, so picking a file clears any pasted text to avoid silently ignoring it.
        it.copy(subtitleUri = uri, subtitleFileName = fileName, pastedTranscript = "")
    }

    fun clearSubtitle() = _state.update { it.copy(subtitleUri = null, subtitleFileName = null) }

    fun onPastedTranscriptChange(text: String) = _state.update {
        if (text.isBlank()) {
            it.copy(pastedTranscript = text)
        } else {
            it.copy(pastedTranscript = text, subtitleUri = null, subtitleFileName = null)
        }
    }

    /** Navigation on success is driven by the UI observing [state].progress becoming [ImportProgress.Done]. */
    fun startImport() {
        val current = _state.value
        val videoUri = current.videoUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                importPipeline.import(
                    title = current.title.ifBlank { "Bez nazwy" },
                    videoUri = videoUri,
                    subtitleUri = current.subtitleUri,
                    pastedTranscript = current.pastedTranscript.ifBlank { null },
                ) { progress -> _state.update { it.copy(progress = progress) } }
            } catch (t: Throwable) {
                Log.e(TAG, "Import failed", t)
                val detail = describe(t)
                _state.update { it.copy(progress = ImportProgress.Failed(detail), error = detail) }
            }
        }
    }

    /**
     * A bare exception class name with no message (e.g. "NoClassDefFoundError") tells a user
     * nothing actionable, and [Throwable.message] alone drops that class name entirely - so this
     * always includes the exception's type, plus every cause in the chain, so a screenshot of the
     * error is actually debuggable without needing device logs.
     */
    private fun describe(t: Throwable): String = buildString {
        var current: Throwable? = t
        var seen = 0
        while (current != null && seen < 5) {
            if (seen > 0) append("\nPrzyczyna: ")
            append(current::class.qualifiedName ?: current.javaClass.name)
            current.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            val next = current.cause
            current = if (next === current) null else next
            seen++
        }
    }

    private companion object {
        private const val TAG = "ImportViewModel"
    }
}
