package com.mnmyounus.ypvm.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager

/**
 * Every restriction in this file is a call against the public, documented
 * DevicePolicyManager / UserManager surface — the same APIs any Android
 * Enterprise MDM uses to run a BYOD work profile. Nothing here is a
 * private API, and nothing here is hidden from the person using the
 * device: whichever mode is active, the UI always shows a visible
 * "Emergency Unlock" path back to Host (see ui/EmergencyUnlockDialog.kt).
 *
 * Deliberately NOT implemented here, by design:
 *  - Anything that would keep Host data hidden from a Host-authenticated
 *    ADB session once the device owner has deliberately turned on
 *    Developer Options / USB debugging on their own primary profile.
 *    DISALLOW_DEBUGGING_FEATURES below is applied to the Guest work
 *    profile only — it stops a Guest session from reaching Host data,
 *    the same way an employer's BYOD policy protects corporate data. It
 *    is not, and was never meant to be, a countermeasure against the
 *    device owner inspecting their own device.
 */
object ProfileManager {

    private const val WHITELIST_PREFS = "ypvm_guest_whitelist"

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, YpvmDeviceAdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    fun onDeviceAdminEnabled(context: Context) {
        val dpm = dpm(context)
        val admin = adminComponent(context)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        // Pin YPVM as the only lock-task-permitted package so it's the
        // screen that reappears immediately after any crash or reboot.
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
    }

    /**
     * Kicks off Android's own managed-provisioning wizard to create the
     * Work Profile that Guest Mode runs in. This is a visible, system-
     * owned flow — the same screen a company's BYOD onboarding shows —
     * YPVM cannot and does not create this silently in the background.
     */
    fun guestProfileProvisioningIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent(context)
            )
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, false)
        }

    fun onGuestProfileProvisioned(context: Context) {
        applyGuestRestrictions(context, whitelist = savedWhitelist(context))
    }

    /**
     * Hides every Work Profile app that isn't on the whitelist, and
     * suspends Host apps for the duration of the Guest session so neither
     * side is reachable from the other. Android's File-Based Encryption
     * already keeps the two profiles' storage on separate encrypted
     * volumes (/data/user/<profile-id>/) — that isolation is inherent to
     * using the Work Profile model at all, not something built here.
     */
    fun applyGuestRestrictions(context: Context, whitelist: Set<String>) {
        val dpm = dpm(context)
        val admin = adminComponent(context)
        val canManage = dpm.isProfileOwnerApp(context.packageName) ||
            dpm.isDeviceOwnerApp(context.packageName)
        if (!canManage) return

        context.packageManager.getInstalledApplications(0).forEach { appInfo ->
            val shouldHide = appInfo.packageName !in whitelist &&
                appInfo.packageName != context.packageName
            runCatching { dpm.setApplicationHidden(admin, appInfo.packageName, shouldHide) }
        }

        // Guest-profile-scoped only — never applied to the primary/Host user.
        dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setPersonalAppsSuspended(admin, true)
        }
    }

    fun exitGuestMode(context: Context) {
        val dpm = dpm(context)
        val admin = adminComponent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { dpm.setPersonalAppsSuspended(admin, false) }
        }
    }

    fun savedWhitelist(context: Context): Set<String> =
        context.getSharedPreferences(WHITELIST_PREFS, Context.MODE_PRIVATE)
            .getStringSet("packages", emptySet()) ?: emptySet()

    fun setWhitelist(context: Context, packages: Set<String>) {
        context.getSharedPreferences(WHITELIST_PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet("packages", packages).apply()
        applyGuestRestrictions(context, packages)
    }

    /**
     * The fail-secure path used by WatchdogService and by a normal Primary-
     * PIN unlock alike: re-arm Android's own keyguard and release the
     * lock-task pin so the stock OS lock screen is fully back in control.
     */
    fun restoreNativeKeyguard(context: Context) {
        val dpm = dpm(context)
        val admin = adminComponent(context)
        runCatching {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setKeyguardDisabled(admin, false)
                dpm.setStatusBarDisabled(admin, false)
            }
        }
    }

    fun disableNativeKeyguard(context: Context) {
        val dpm = dpm(context)
        val admin = adminComponent(context)
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.setKeyguardDisabled(admin, true)
            dpm.setStatusBarDisabled(admin, true)
        }
    }
}
