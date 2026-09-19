package com.voicechoicer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        CharacterEntity::class,
        FragmentEntity::class,
        PlayerEntity::class,
        TakeEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun characterDao(): CharacterDao
    abstract fun fragmentDao(): FragmentDao
    abstract fun playerDao(): PlayerDao
    abstract fun takeDao(): TakeDao

    companion object {
        const val DB_NAME = "voice_choicer.db"
    }
}
