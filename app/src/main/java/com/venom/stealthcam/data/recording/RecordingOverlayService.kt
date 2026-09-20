package com.venom.stealthcam.data.recording

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint

/**
 * A minimal [Service] that draws a pulsing 12dp red dot in the top-right corner of the
 * screen using [WindowManager] SYSTEM_ALERT_WINDOW overlay.
 *
 * Uses standard Android Views and ObjectAnimator instead of Compose to bypass all
 * WindowManager/Compose lifecycle complexity.
 */
@AndroidEntryPoint
class RecordingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var animator: ValueAnimator? = null

    override fun onCreate() {
        super.onCreate()
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        val wm = getSystemService(WindowManager::class.java).also { windowManager = it }

        val params = LayoutParams(
            /* w= */ LayoutParams.WRAP_CONTENT,
            /* h= */ LayoutParams.WRAP_CONTENT,
            /* type= */ LayoutParams.TYPE_APPLICATION_OVERLAY,
            /* flags= */ LayoutParams.FLAG_NOT_FOCUSABLE
                    or LayoutParams.FLAG_NOT_TOUCHABLE
                    or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            /* format= */ PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16  // 16px right margin
            y = 64  // 64px top margin
        }

        // 12dp in pixels
        val sizePx = (12 * resources.displayMetrics.density).toInt()

        val dotView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            }
        }

        overlayContainer = FrameLayout(this).apply {
            addView(dotView)
        }

        animator = ValueAnimator.ofFloat(1f, 0.2f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                dotView.alpha = anim.animatedValue as Float
            }
            start()
        }

        wm.addView(overlayContainer, params)
    }

    private fun removeOverlay() {
        animator?.cancel()
        animator = null
        
        overlayContainer?.let {
            runCatching { windowManager?.removeViewImmediate(it) }
        }
        overlayContainer = null
        windowManager = null
    }
}
