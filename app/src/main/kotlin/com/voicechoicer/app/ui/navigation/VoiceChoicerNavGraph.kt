package com.voicechoicer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voicechoicer.app.ui.characters.CharactersScreen
import com.voicechoicer.app.ui.export.ExportScreen
import com.voicechoicer.app.ui.importflow.ImportScreen
import com.voicechoicer.app.ui.players.PlayersScreen
import com.voicechoicer.app.ui.projecthome.ProjectHomeScreen
import com.voicechoicer.app.ui.projects.ProjectListScreen
import com.voicechoicer.app.ui.recording.RecordingScreen

@Composable
fun VoiceChoicerNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.PROJECT_LIST) {
        composable(Routes.PROJECT_LIST) {
            ProjectListScreen(
                onOpenProject = { navController.navigate(Routes.projectHome(it)) },
                onImportNew = { navController.navigate(Routes.IMPORT) },
            )
        }
        composable(Routes.IMPORT) {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onImported = { projectId ->
                    navController.navigate(Routes.projectHome(projectId)) {
                        popUpTo(Routes.PROJECT_LIST)
                    }
                },
            )
        }
        composable(
            Routes.PROJECT_HOME,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            ProjectHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenCharacters = { navController.navigate(Routes.characters(it)) },
                onOpenPlayers = { navController.navigate(Routes.players(it)) },
                onOpenRecording = { navController.navigate(Routes.recording(it)) },
                onOpenExport = { navController.navigate(Routes.export(it)) },
            )
        }
        composable(
            Routes.CHARACTERS,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            CharactersScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.PLAYERS,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            PlayersScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.RECORDING,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            RecordingScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.EXPORT,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
    }
}
