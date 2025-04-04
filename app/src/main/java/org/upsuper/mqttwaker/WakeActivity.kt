package org.upsuper.mqttwaker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.core.net.toUri

class WakeActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WakeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Minimal empty layout - this activity is just for waking the screen
        setContentView(R.layout.activity_wake)

        Log.d(TAG, "WakeActivity created, screen should be on")

        // Get URL from preferences and open it using the main thread handler
        // This ensures more reliable browser launching
        Handler(Looper.getMainLooper()).postDelayed({
            openBrowserUrl()

            // Give the browser a moment to open before finishing
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Finishing WakeActivity")
                finish()
            }, 2000) // 2 seconds delay
        }, 500) // small delay to ensure screen is fully on
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
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Add a category to open in browser
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open URL: $browserUrl", e)
            }
        }
    }
}
