package com.voicechoicer.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("UPDATE projects SET dubbedVideoPath = :path WHERE id = :projectId")
    suspend fun setDubbedVideoPath(projectId: Long, path: String?)
}

@Dao
interface CharacterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(characters: List<CharacterEntity>): List<Long>

    @Insert
    suspend fun insert(character: CharacterEntity): Long

    @Update
    suspend fun update(character: CharacterEntity)

    @Delete
    suspend fun delete(character: CharacterEntity)

    @Query("SELECT * FROM characters WHERE projectId = :projectId ORDER BY id")
    fun observeByProject(projectId: Long): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: Long): CharacterEntity?

    @Query("UPDATE characters SET assignedPlayerId = :playerId WHERE id = :characterId")
    suspend fun assignPlayer(characterId: Long, playerId: Long?)
}

@Dao
interface FragmentDao {
    @Insert
    suspend fun insertAll(fragments: List<FragmentEntity>): List<Long>

    @Update
    suspend fun update(fragment: FragmentEntity)

    @Query("SELECT * FROM fragments WHERE projectId = :projectId ORDER BY orderIndex")
    fun observeByProject(projectId: Long): Flow<List<FragmentEntity>>

    @Query("SELECT * FROM fragments WHERE characterId = :characterId ORDER BY orderIndex")
    fun observeByCharacter(characterId: Long): Flow<List<FragmentEntity>>

    @Query("SELECT * FROM fragments WHERE projectId = :projectId ORDER BY orderIndex")
    suspend fun getByProject(projectId: Long): List<FragmentEntity>

    @Query("UPDATE fragments SET characterId = :characterId WHERE id = :fragmentId")
    suspend fun reassignCharacter(fragmentId: Long, characterId: Long)

    @Query("UPDATE fragments SET text = :text WHERE id = :fragmentId")
    suspend fun updateText(fragmentId: Long, text: String)
}

@Dao
interface PlayerDao {
    @Insert
    suspend fun insert(player: PlayerEntity): Long

    @Delete
    suspend fun delete(player: PlayerEntity)

    @Query("SELECT * FROM players WHERE projectId = :projectId ORDER BY id")
    fun observeByProject(projectId: Long): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE projectId = :projectId ORDER BY id")
    suspend fun getByProject(projectId: Long): List<PlayerEntity>
}

@Dao
interface TakeDao {
    @Insert
    suspend fun insert(take: TakeEntity): Long

    @Delete
    suspend fun delete(take: TakeEntity)

    @Query("SELECT * FROM takes WHERE fragmentId = :fragmentId ORDER BY createdAt DESC")
    fun observeForFragment(fragmentId: Long): Flow<List<TakeEntity>>

    @Query("SELECT * FROM takes WHERE fragmentId = :fragmentId AND isSelected = 1 LIMIT 1")
    suspend fun getSelectedForFragment(fragmentId: Long): TakeEntity?

    @Query("UPDATE takes SET isSelected = 0 WHERE fragmentId = :fragmentId")
    suspend fun clearSelection(fragmentId: Long)

    @Query("UPDATE takes SET isSelected = 1 WHERE id = :takeId")
    suspend fun markSelected(takeId: Long)

    @Transaction
    suspend fun selectTake(fragmentId: Long, takeId: Long) {
        clearSelection(fragmentId)
        markSelected(takeId)
    }

    @Query(
        """SELECT takes.* FROM takes
           INNER JOIN fragments ON fragments.id = takes.fragmentId
           WHERE fragments.projectId = :projectId AND takes.isSelected = 1""",
    )
    suspend fun getSelectedTakesForProject(projectId: Long): List<TakeEntity>

    @Query(
        """SELECT COUNT(DISTINCT takes.fragmentId) FROM takes
           INNER JOIN fragments ON fragments.id = takes.fragmentId
           WHERE fragments.projectId = :projectId AND takes.isSelected = 1""",
    )
    fun observeRecordedFragmentCount(projectId: Long): Flow<Int>
}
