package com.voicechoicer.app.ui.navigation

object Routes {
    const val PROJECT_LIST = "projects"
    const val IMPORT = "import"
    const val PROJECT_HOME = "project/{projectId}"
    const val CHARACTERS = "project/{projectId}/characters"
    const val PLAYERS = "project/{projectId}/players"
    const val RECORDING = "project/{projectId}/recording"
    const val EXPORT = "project/{projectId}/export"

    fun projectHome(projectId: Long) = "project/$projectId"
    fun characters(projectId: Long) = "project/$projectId/characters"
    fun players(projectId: Long) = "project/$projectId/players"
    fun recording(projectId: Long) = "project/$projectId/recording"
    fun export(projectId: Long) = "project/$projectId/export"

    const val ARG_PROJECT_ID = "projectId"
}
