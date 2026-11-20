package dev.aprax.tmaii.sync

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.aprax.tmaii.ApraxApp
import dev.aprax.tmaii.MainActivity
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import dev.aprax.tmaii.core.repository.ActiveMailboxStore
import dev.aprax.tmaii.core.repository.MailboxRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class InboxSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mailboxRepo: MailboxRepository,
    private val settings: SettingsDataStore,
    private val activeStore: ActiveMailboxStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val notificationsOn = settings.notificationsEnabled.first()
        if (!notificationsOn) return Result.success()

        val mailboxId = activeStore.current()
            ?: mailboxRepo.observeHistory().first().firstOrNull()?.id
            ?: return Result.success()

        return try {
            val before = mailboxRepo.observeCachedMessages(mailboxId).first()
                .filter { !it.seen }
                .map { it.providerMessageId }
                .toSet()

            val messages = mailboxRepo.refreshMessages(mailboxId)
            val newOnes = messages.filter { it.id !in before && !it.seen }

            if (newOnes.isNotEmpty() && hasNotifyPermission()) {
                val otp = newOnes.firstOrNull { !it.otp?.get("code").isNullOrBlank() }
                if (otp != null) {
                    notify(
                        id = otp.id.hashCode(),
                        channel = ApraxApp.CHANNEL_OTP,
                        title = "OTP ${otp.otp?.get("code")}",
                        text = otp.subject ?: otp.from,
                    )
                } else {
                    val first = newOnes.first()
                    notify(
                        id = first.id.hashCode(),
                        channel = ApraxApp.CHANNEL_NEW_MAIL,
                        title = "New mail from ${first.from}",
                        text = first.subject ?: "(no subject)",
                    )
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun hasNotifyPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notify(id: Int, channel: String, title: String, text: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    companion object {
        private const val WORK_NAME = "inbox_sync"

        fun schedule(context: Context, intervalMinutes: Long = 15) {
            val request = PeriodicWorkRequestBuilder<InboxSyncWorker>(
                intervalMinutes.coerceAtLeast(15),
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
