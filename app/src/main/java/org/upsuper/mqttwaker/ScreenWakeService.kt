package org.upsuper.mqttwaker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout

class ScreenWakeService : Service() {
    companion object {
        private const val TAG = "ScreenWakeService"
    }
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: FrameLayout

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Screen wake service started")

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No permission to draw overlays")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            overlayView = FrameLayout(this)

            val params = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                LayoutParams.TYPE_APPLICATION_OVERLAY,
                LayoutParams.FLAG_KEEP_SCREEN_ON or
                        LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER

            windowManager.addView(overlayView, params)

            // Start WakeActivity
            val wakeIntent = Intent(this, WakeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(wakeIntent)

            // Remove the overlay view after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                removeOverlayAndStop()
            }, 3000)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun removeOverlayAndStop() {
        try {
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
