package dev.aprax.tmaii.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_mailboxes")
data class LocalMailboxEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val address: String,
    val createdAt: Long,
    val lastSyncAt: Long? = null,
    val label: String? = null,
)

@Entity(
    tableName = "local_messages",
    indices = [Index(value = ["mailboxId", "providerMessageId"], unique = true)],
)
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val mailboxId: String,
    val providerMessageId: String,
    val sender: String,
    val senderName: String? = null,
    val subject: String? = null,
    val receivedAt: Long? = null,
    val seen: Boolean = false,
    val hasOtp: Boolean = false,
    val otpCode: String? = null,
    val otpSource: String? = null,
    val hasVerificationLink: Boolean = false,
)
