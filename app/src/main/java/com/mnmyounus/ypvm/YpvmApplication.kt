package com.mnmyounus.ypvm

import android.app.Application
import android.content.Intent
import android.os.Build
import com.mnmyounus.ypvm.watchdog.WatchdogService

/**
 * Entry point for the whole fail-secure guarantee: the watchdog starts the
 * moment this process is alive at all, independent of whether the lock
 * screen itself has managed to draw yet.
 */
class YpvmApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startWatchdog()
    }

    private fun startWatchdog() {
        val intent = Intent(this, WatchdogService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
