package dev.aprax.tmaii.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalMailboxEntity::class, LocalMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ApraxDatabase : RoomDatabase() {
    abstract fun mailboxDao(): MailboxDao
    abstract fun messageDao(): MessageDao
}
