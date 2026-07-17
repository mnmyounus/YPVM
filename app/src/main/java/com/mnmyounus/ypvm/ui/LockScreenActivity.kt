package com.mnmyounus.ypvm.ui

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.mnmyounus.ypvm.R
import com.mnmyounus.ypvm.admin.ProfileManager
import com.mnmyounus.ypvm.databinding.ActivityLockScreenBinding
import com.mnmyounus.ypvm.security.PinVault

class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var pinVault: PinVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pinVault = PinVault(this)

        enterLockTaskModeIfPermitted()

        binding.confirmButton.setOnClickListener { submitPin() }

        // Visible, labeled, reachable by anyone looking at the screen — the
        // deliberate replacement for a hidden tap-combo. Its existence is
        // not a secret; only the Primary PIN behind it does anything.
        binding.emergencyUnlockButton.setOnClickListener { showEmergencyUnlock() }

        // This is the lock screen — there is nothing to "go back" to
        // except through a successful PIN entry or the visible Emergency
        // Unlock path, so the system Back gesture/button is a no-op here.
        onBackPressedDispatcher.addCallback(this) { /* intentionally empty */ }
    }

    private fun enterLockTaskModeIfPermitted() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isLockTaskPermitted(packageName)) {
            runCatching { startLockTask() }
        }
    }

    private fun submitPin() {
        val remaining = maxOf(
            pinVault.lockoutRemainingMs(PinVault.PinRole.HOST),
            pinVault.lockoutRemainingMs(PinVault.PinRole.GUEST)
        )
        if (remaining > 0) {
            toastLockout(remaining)
            return
        }

        val pin = binding.pinInput.text?.toString().orEmpty().toCharArray()
        binding.pinInput.text?.clear()
        if (pin.isEmpty()) return

        when {
            pinVault.verify(PinVault.PinRole.HOST, pin.copyOf()) -> {
                pinVault.clearFailedAttempts(PinVault.PinRole.HOST)
                unlockToHost()
            }
            pinVault.verify(PinVault.PinRole.GUEST, pin.copyOf()) -> {
                pinVault.clearFailedAttempts(PinVault.PinRole.GUEST)
                unlockToGuest()
            }
            else -> {
                val count = pinVault.recordFailedAttempt(PinVault.PinRole.HOST)
                Toast.makeText(this, R.string.incorrect_pin, Toast.LENGTH_SHORT).show()
                if (count >= 5) toastLockout(pinVault.lockoutRemainingMs(PinVault.PinRole.HOST))
            }
        }
        pin.fill('0')
    }

    private fun unlockToHost() {
        ProfileManager.restoreNativeKeyguard(this)
        ProfileManager.exitGuestMode(this)
        if (isInLockTaskMode()) runCatching { stopLockTask() }
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun unlockToGuest() {
        startActivity(Intent(this, GuestLauncherActivity::class.java))
        finish()
    }

    /** Same PIN check, same security guarantee as the main entry field —
     *  the only difference from a normal unlock is that this path is
     *  reachable without knowing which PIN you're about to type. */
    private fun showEmergencyUnlock() {
        EmergencyUnlockDialog.show(this, pinVault) { verified -> if (verified) unlockToHost() }
    }

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun toastLockout(remainingMs: Long) {
        val seconds = (remainingMs / 1000).coerceAtLeast(1)
        Toast.makeText(this, getString(R.string.lockout_message, seconds), Toast.LENGTH_SHORT).show()
    }
}
