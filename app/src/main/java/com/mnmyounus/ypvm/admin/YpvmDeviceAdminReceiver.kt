package com.mnmyounus.ypvm.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * YPVM has no effect on the device until this admin is activated as Device
 * Owner, which Android only allows on a device with no account signed in
 * yet (factory-reset state, or the QR/zero-touch managed-provisioning
 * flow):
 *
 *   adb shell dpm set-device-owner \
 *       com.mnmyounus.ypvm/.admin.YpvmDeviceAdminReceiver
 *
 * That constraint is Android's, not YPVM's — it exists precisely so a
 * device owner can't be installed silently onto a phone someone is already
 * using. See docs/ARCHITECTURE.md for the full provisioning walkthrough.
 */
class YpvmDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        ProfileManager.onDeviceAdminEnabled(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        ProfileManager.onGuestProfileProvisioned(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // If admin rights are ever revoked, fail secure: hand control
        // straight back to Android's own keyguard rather than leaving a
        // half-configured custom screen behind.
        ProfileManager.restoreNativeKeyguard(context)
    }
}
