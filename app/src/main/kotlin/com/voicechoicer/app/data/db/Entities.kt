package com.voicechoicer.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Path to the source clip, copied into app-private storage on import. */
    val localVideoPath: String,
    val durationMs: Long,
    val createdAt: Long,
    /** Path to the last successfully exported dubbed video, if any. */
    val dubbedVideoPath: String? = null,
)

@Entity(
    tableName = "characters",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    /** Stable detection key (e.g. "name_john", "dash_speaker_1", "unknown"); user-defined characters get a random key. */
    val key: String,
    val name: String,
    val colorArgb: Int,
    val assignedPlayerId: Long? = null,
)

@Entity(
    tableName = "fragments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("characterId")],
)
data class FragmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val orderIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val characterId: Long,
)

@Entity(
    tableName = "players",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
)

@Entity(
    tableName = "takes",
    foreignKeys = [
        ForeignKey(
            entity = FragmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["fragmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fragmentId"), Index("playerId")],
)
data class TakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fragmentId: Long,
    val playerId: Long,
    val wavFilePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val isSelected: Boolean,
)
