package dev.aprax.tmaii

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ApraxApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_NEW_MAIL,
                    getString(R.string.notification_channel_new_mail),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHANNEL_OTP,
                    getString(R.string.notification_channel_otp),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            ),
        )
    }

    companion object {
        const val CHANNEL_NEW_MAIL = "new_mail"
        const val CHANNEL_OTP = "otp_codes"
    }
}
