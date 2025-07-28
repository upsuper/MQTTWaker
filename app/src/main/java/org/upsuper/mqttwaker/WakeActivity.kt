package org.upsuper.mqttwaker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.core.net.toUri

class WakeActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WakeActivity"
        private const val AUTO_CLOSE_DELAY = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var autoCloseRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wake)

        Log.d(TAG, "WakeActivity created, screen should be on")

        findViewById<Button>(R.id.btnClose).setOnClickListener {
            Log.d(TAG, "Manual close button pressed")
            closeActivity()
        }

        scheduleAutoClose()
        openBrowserUrl()
    }

    private fun openBrowserUrl() {
        // Get URL from preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val browserUrl = prefs.getString("browser_url", "") ?: ""

        // Open the configured URL if set
        if (browserUrl.isNotBlank()) {
            try {
                Log.d(TAG, "Opening URL: $browserUrl")
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = browserUrl.toUri()
                    // Use better flags to ensure the browser opens in a separate task
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                startActivity(browserIntent)
                Log.d(TAG, "Browser intent started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open URL: $browserUrl", e)
            }
        } else {
            Log.w(TAG, "No browser URL configured")
        }
    }

    private fun scheduleAutoClose() {
        Log.d(TAG, "Scheduling auto-close in ${AUTO_CLOSE_DELAY}ms")
        autoCloseRunnable = Runnable {
            Log.d(TAG, "Auto-closing WakeActivity")
            closeActivity()
        }
        handler.postDelayed(autoCloseRunnable!!, AUTO_CLOSE_DELAY)
    }

    private fun closeActivity() {
        Log.d(TAG, "Closing WakeActivity")
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
        autoCloseRunnable = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
        autoCloseRunnable = null
        Log.d(TAG, "WakeActivity destroyed")
    }
}
