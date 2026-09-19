package com.voicechoicer.app.data

import com.voicechoicer.app.data.db.AppDatabase
import com.voicechoicer.app.data.db.CharacterEntity
import com.voicechoicer.app.data.db.FragmentEntity
import com.voicechoicer.app.data.db.PlayerEntity
import com.voicechoicer.app.data.db.ProjectEntity
import com.voicechoicer.app.data.db.TakeEntity
import com.voicechoicer.app.data.files.AppFileStore
import com.voicechoicer.core.model.DetectedCharacter
import com.voicechoicer.core.model.Fragment
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ProjectRepository @Inject constructor(
    private val db: AppDatabase,
    private val fileStore: AppFileStore,
) {

    fun observeProjects(): Flow<List<ProjectEntity>> = db.projectDao().observeAll()

    fun observeProject(projectId: Long): Flow<ProjectEntity?> = db.projectDao().observeById(projectId)

    suspend fun getProject(projectId: Long): ProjectEntity? = db.projectDao().getById(projectId)

    /**
     * Persists a freshly-imported clip: the copied video file, the detected
     * characters (with generated colors) and the fragments referencing them,
     * all in one shot so the UI only ever sees a fully-formed project.
     */
    suspend fun createProject(
        title: String,
        videoFile: File,
        durationMs: Long,
        detectedCharacters: List<DetectedCharacter>,
        fragments: List<Fragment>,
    ): Long {
        val projectId = db.projectDao().insert(
            ProjectEntity(
                title = title,
                localVideoPath = videoFile.absolutePath,
                durationMs = durationMs,
                createdAt = System.currentTimeMillis(),
            ),
        )

        val keyToId = mutableMapOf<String, Long>()
        detectedCharacters.forEachIndexed { index, character ->
            val id = db.characterDao().insert(
                CharacterEntity(
                    projectId = projectId,
                    key = character.key,
                    name = character.displayName,
                    colorArgb = CharacterColors.forIndex(index),
                ),
            )
            keyToId[character.key] = id
        }

        val fragmentEntities = fragments.map { fragment ->
            FragmentEntity(
                projectId = projectId,
                orderIndex = fragment.orderIndex,
                startMs = fragment.startMs,
                endMs = fragment.endMs,
                text = fragment.text,
                characterId = keyToId.getValue(fragment.characterKey),
            )
        }
        db.fragmentDao().insertAll(fragmentEntities)

        return projectId
    }

    suspend fun deleteProject(project: ProjectEntity) {
        fileStore.deleteQuietly(project.localVideoPath)
        fileStore.deleteQuietly(project.dubbedVideoPath)
        db.projectDao().delete(project)
    }

    // ---- Characters ----

    fun observeCharacters(projectId: Long): Flow<List<CharacterEntity>> = db.characterDao().observeByProject(projectId)

    suspend fun renameCharacter(character: CharacterEntity, newName: String) {
        db.characterDao().update(character.copy(name = newName))
    }

    suspend fun addCharacter(projectId: Long, name: String, colorIndex: Int): Long =
        db.characterDao().insert(
            CharacterEntity(
                projectId = projectId,
                key = "manual_${System.nanoTime()}",
                name = name,
                colorArgb = CharacterColors.forIndex(colorIndex),
            ),
        )

    suspend fun assignPlayerToCharacter(characterId: Long, playerId: Long?) {
        db.characterDao().assignPlayer(characterId, playerId)
    }

    // ---- Fragments ----

    fun observeFragments(projectId: Long): Flow<List<FragmentEntity>> = db.fragmentDao().observeByProject(projectId)

    fun observeFragmentsForCharacter(characterId: Long): Flow<List<FragmentEntity>> =
        db.fragmentDao().observeByCharacter(characterId)

    suspend fun reassignFragmentCharacter(fragmentId: Long, characterId: Long) {
        db.fragmentDao().reassignCharacter(fragmentId, characterId)
    }

    suspend fun updateFragmentText(fragmentId: Long, text: String) {
        db.fragmentDao().updateText(fragmentId, text)
    }

    suspend fun getFragments(projectId: Long): List<FragmentEntity> = db.fragmentDao().getByProject(projectId)

    // ---- Players ----

    fun observePlayers(projectId: Long): Flow<List<PlayerEntity>> = db.playerDao().observeByProject(projectId)

    suspend fun addPlayer(projectId: Long, name: String): Long = db.playerDao().insert(PlayerEntity(projectId = projectId, name = name))

    suspend fun removePlayer(player: PlayerEntity) = db.playerDao().delete(player)

    // ---- Takes ----

    fun observeTakesForFragment(fragmentId: Long): Flow<List<TakeEntity>> = db.takeDao().observeForFragment(fragmentId)

    suspend fun getSelectedTake(fragmentId: Long): TakeEntity? = db.takeDao().getSelectedForFragment(fragmentId)

    fun newTakeFile(fragmentId: Long): File = fileStore.newTakeFile(fragmentId)

    suspend fun saveTake(fragmentId: Long, playerId: Long, wavFile: File, durationMs: Long): Long {
        val takeId = db.takeDao().insert(
            TakeEntity(
                fragmentId = fragmentId,
                playerId = playerId,
                wavFilePath = wavFile.absolutePath,
                durationMs = durationMs,
                createdAt = System.currentTimeMillis(),
                isSelected = true,
            ),
        )
        db.takeDao().selectTake(fragmentId, takeId)
        return takeId
    }

    suspend fun selectTake(fragmentId: Long, takeId: Long) = db.takeDao().selectTake(fragmentId, takeId)

    suspend fun deleteTake(take: TakeEntity) {
        fileStore.deleteQuietly(take.wavFilePath)
        db.takeDao().delete(take)
    }

    suspend fun getSelectedTakesForProject(projectId: Long): List<TakeEntity> = db.takeDao().getSelectedTakesForProject(projectId)

    fun observeRecordedFragmentCount(projectId: Long): Flow<Int> = db.takeDao().observeRecordedFragmentCount(projectId)

    fun newExportFile(projectId: Long): File = fileStore.newExportFile(projectId)

    suspend fun setDubbedVideoPath(projectId: Long, path: String?) = db.projectDao().setDubbedVideoPath(projectId, path)
}
