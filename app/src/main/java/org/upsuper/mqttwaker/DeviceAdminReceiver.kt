package org.upsuper.mqttwaker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MQTTWakerDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "MQTTWakerAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled")
    }
}
