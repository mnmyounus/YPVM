package com.mnmyounus.ypvm.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.mnmyounus.ypvm.admin.ProfileManager
import com.mnmyounus.ypvm.databinding.ActivityGuestLauncherBinding
import com.mnmyounus.ypvm.security.PinVault

class GuestLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuestLauncherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuestLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appGrid.layoutManager = GridLayoutManager(this, 4)
        binding.appGrid.adapter = WhitelistedAppAdapter(whitelistedLaunchableApps()) { appInfo ->
            packageManager.getLaunchIntentForPackage(appInfo.packageName)?.let(::startActivity)
        }

        // Same visible Emergency Unlock as the PIN screen — reachable from
        // inside an active Guest session too, not only before entering one.
        binding.emergencyUnlockButton.setOnClickListener {
            EmergencyUnlockDialog.show(this, PinVault(this)) { verified ->
                if (verified) returnToLockScreenAsHost()
            }
        }
    }

    private fun returnToLockScreenAsHost() {
        ProfileManager.restoreNativeKeyguard(this)
        ProfileManager.exitGuestMode(this)
        startActivity(
            Intent(this, LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun whitelistedLaunchableApps(): List<ApplicationInfo> {
        val whitelist = ProfileManager.savedWhitelist(this)
        return packageManager.getInstalledApplications(0).filter { app ->
            app.packageName in whitelist &&
                packageManager.getLaunchIntentForPackage(app.packageName) != null
        }
    }
}
