package com.voicechoicer.app.di

import android.content.Context
import androidx.room.Room
import com.voicechoicer.app.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME).build()

    @Provides
    fun provideProjectDao(db: AppDatabase) = db.projectDao()

    @Provides
    fun provideCharacterDao(db: AppDatabase) = db.characterDao()

    @Provides
    fun provideFragmentDao(db: AppDatabase) = db.fragmentDao()

    @Provides
    fun providePlayerDao(db: AppDatabase) = db.playerDao()

    @Provides
    fun provideTakeDao(db: AppDatabase) = db.takeDao()
}
