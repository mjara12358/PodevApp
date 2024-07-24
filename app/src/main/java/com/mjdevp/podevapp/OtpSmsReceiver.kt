package com.mjdevp.podevapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class OtpSmsReceiver(private var authManager: AuthManager? = null) : BroadcastReceiver() {

    fun setAuthManager(authManager: AuthManager) {
        this.authManager = authManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val authManager = this.authManager
        if (authManager == null) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val msg = sms.messageBody
            // Check if the message contains the specific pattern
            if (msg.contains("podev-app.firebaseapp.com")) {
                // Split the message to extract the OTP
                val parts = msg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                // Iterate through the parts to find the OTP
                for (part in parts) {
                    if (part.length == 6 && part.matches(Regex("[0-9]+"))) {
                        // If the part is 6 characters long and contains only digits, it's likely the OTP
                        /*Log.d("OTP", part)*/
                        authManager.singWhitNumPhone(part)
                        break
                    }
                }
            }
        }
    }
}