package com.mnmyounus.ypvm.watchdog

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.mnmyounus.ypvm.R
import com.mnmyounus.ypvm.admin.ProfileManager
import com.mnmyounus.ypvm.ui.LockScreenActivity

/**
 * The whole fail-secure guarantee lives here: this service's only job is
 * making sure YPVM's own lock screen is the thing on top of the stack.
 *
 *  - If YPVM crashes, it's relaunched within ~1 second.
 *  - If it keeps failing to come back cleanly, this stops trying and hands
 *    control back to Android's real keyguard instead — never an unlocked
 *    device, never a stuck half-state. See ProfileManager.restoreNativeKeyguard().
 */
class WatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var consecutiveFailures = 0

    private val checkIntervalMs = 800L
    private val maxFailuresBeforeFallback = 5

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkAndRelaunch()
            handler.postDelayed(this, checkIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(watchdogRunnable)
        handler.post(watchdogRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun checkAndRelaunch() {
        if (isLockScreenForeground()) {
            consecutiveFailures = 0
            return
        }

        if (consecutiveFailures >= maxFailuresBeforeFallback) {
            // Relaunching isn't sticking — stop hammering it and fail
            // secure to the OS's own keyguard rather than leaving the
            // device in an ambiguous state.
            ProfileManager.restoreNativeKeyguard(applicationContext)
            return
        }

        consecutiveFailures++
        val relaunch = Intent(applicationContext, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(relaunch) }
    }

    private fun isLockScreenForeground(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.appTasks.any { task ->
            task.taskInfo?.topActivity?.className == LockScreenActivity::class.java.name
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "ypvm_watchdog"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "YPVM Guard", NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.watchdog_notification_title))
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdogRunnable)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
    }
}
