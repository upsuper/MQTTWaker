package org.upsuper.mqttwaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val isServiceEnabled = prefs.getBoolean("service_enabled", false)

            if (isServiceEnabled) {
                val serviceIntent = Intent(context, MQTTService::class.java)
                context.startService(serviceIntent)
            }
        }
    }
}
