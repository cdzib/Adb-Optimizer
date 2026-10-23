package com.optimizer.tools.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.optimizer.tools.AdbOptimizerApplication
import com.optimizer.tools.MainActivity
import com.optimizer.tools.R

/**
 * Mantiene el proceso en primer plano mientras hay una sesion ADB activa.
 * La misma notificacion muestra el progreso cuando se instalan APKs.
 */
class AdbSessionService : Service() {
    private var sessionText: String = "Sesión ADB activa"
    private var progressText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopAdbSessions()
            return START_NOT_STICKY
        }
        intent?.getStringExtra(EXTRA_SESSION_TEXT)?.let { sessionText = it }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun stopAdbSessions() {
        // Prevent a finishing installation from publishing this notification again.
        if (instance === this) instance = null
        val container = (application as AdbOptimizerApplication).container
        container.clearActiveSession()
        container.disconnect()
        progressText = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    internal fun updateProgress(progress: String?) {
        progressText = progress
        notifyChanged()
    }

    internal fun clearProgress() {
        progressText = null
        notifyChanged()
    }

    private fun notifyChanged() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val progress = progressText
        val installing = !progress.isNullOrBlank()
        val percent =
            PERCENT_REGEX
                .find(progress.orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_optimizer)
            .setContentTitle(
                getString(if (installing) R.string.session_notification_installing else R.string.session_notification_connected),
            ).setContentText(sessionText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(sessionText),
            ).apply {
                if (installing) setProgress(100, percent ?: 0, percent == null)
            }.setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.session_notification_stop),
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, AdbSessionService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Sesión ADB e instalaciones",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "adb-session"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_SESSION_TEXT = "session_text"
        private const val ACTION_STOP = "com.optimizer.tools.action.STOP_ADB_SESSION"
        private val PERCENT_REGEX = Regex("(\\d{1,3})%")

        @Volatile
        private var instance: AdbSessionService? = null

        fun start(
            context: Context,
            sessionText: String,
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AdbSessionService::class.java)
                    .putExtra(EXTRA_SESSION_TEXT, sessionText),
            )
        }

        fun ensureStarted(context: Context) {
            if (instance == null) start(context, context.getString(R.string.session_notification_active))
        }

        fun updateProgress(progress: String?) {
            instance?.updateProgress(progress)
        }

        fun clearProgress() {
            instance?.clearProgress()
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AdbSessionService::class.java))
        }
    }
}
