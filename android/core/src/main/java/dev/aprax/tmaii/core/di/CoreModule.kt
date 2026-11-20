package dev.aprax.tmaii.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.aprax.tmaii.core.database.ApraxDatabase
import dev.aprax.tmaii.core.database.MailboxDao
import dev.aprax.tmaii.core.database.MessageDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ApraxDatabase =
        Room.databaseBuilder(context, ApraxDatabase::class.java, "aprax.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMailboxDao(db: ApraxDatabase): MailboxDao = db.mailboxDao()

    @Provides
    fun provideMessageDao(db: ApraxDatabase): MessageDao = db.messageDao()
}
