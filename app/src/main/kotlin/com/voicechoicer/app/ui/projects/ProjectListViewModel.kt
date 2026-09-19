package com.voicechoicer.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.db.ProjectEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val repository: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch { repository.deleteProject(project) }
    }
}
