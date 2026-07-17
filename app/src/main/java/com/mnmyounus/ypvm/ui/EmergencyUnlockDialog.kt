package com.mnmyounus.ypvm.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import com.mnmyounus.ypvm.R
import com.mnmyounus.ypvm.security.PinVault

/**
 * A labeled "Emergency Unlock" entry point, reachable from both the lock
 * screen and the Guest launcher. This exists specifically so that no one
 * using the device needs to already know a Host mode exists in order to
 * see that it does — only the Primary PIN behind it actually works.
 *
 * See docs/ARCHITECTURE.md → "Design decisions" for why this replaced an
 * earlier hidden-tap-combo version of the same recovery path.
 */
object EmergencyUnlockDialog {

    fun show(context: Context, pinVault: PinVault, onResult: (Boolean) -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = context.getString(R.string.emergency_unlock_hint)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.emergency_unlock_title)
            .setMessage(R.string.emergency_unlock_message)
            .setView(input)
            .setPositiveButton(R.string.unlock) { _, _ ->
                val pin = input.text?.toString().orEmpty().toCharArray()
                val verified = pinVault.verify(PinVault.PinRole.HOST, pin.copyOf())
                pin.fill('0')
                if (verified) {
                    pinVault.clearFailedAttempts(PinVault.PinRole.HOST)
                } else {
                    pinVault.recordFailedAttempt(PinVault.PinRole.HOST)
                    Toast.makeText(context, R.string.incorrect_pin, Toast.LENGTH_SHORT).show()
                }
                onResult(verified)
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(true)
            .show()
    }
}
