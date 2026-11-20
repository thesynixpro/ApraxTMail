package dev.aprax.tmaii.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MailboxDao {
    @Query("SELECT * FROM local_mailboxes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LocalMailboxEntity>>

    @Query("SELECT * FROM local_mailboxes ORDER BY createdAt DESC")
    suspend fun getAll(): List<LocalMailboxEntity>

    @Query("SELECT * FROM local_mailboxes WHERE id = :id")
    suspend fun findById(id: String): LocalMailboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalMailboxEntity)

    @Query("DELETE FROM local_mailboxes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM local_mailboxes")
    suspend fun clear()

    @Query("UPDATE local_mailboxes SET lastSyncAt = :ts WHERE id = :id")
    suspend fun markSynced(id: String, ts: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM local_messages WHERE mailboxId = :mailboxId ORDER BY receivedAt DESC")
    fun observeForMailbox(mailboxId: String): Flow<List<LocalMessageEntity>>

    @Query("SELECT * FROM local_messages WHERE mailboxId = :mailboxId ORDER BY receivedAt DESC")
    suspend fun getForMailbox(mailboxId: String): List<LocalMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalMessageEntity>)

    @Query("DELETE FROM local_messages WHERE mailboxId = :mailboxId")
    suspend fun clearForMailbox(mailboxId: String)

    @Query("DELETE FROM local_messages")
    suspend fun clearAll()
}
