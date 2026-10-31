package io.github.ceigt.geolocation.manager.route

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.ceigt.geolocation.data.model.route.RouteRunMode
import io.github.ceigt.geolocation.data.model.route.RouteTravelMode
import kotlin.math.*

class JoystickOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    fun isShowing(): Boolean = overlayView != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(context)) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }
        params = lp

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val dialSizePx = (160 * context.resources.displayMetrics.density).toInt()
        val knobSizePx = (50 * context.resources.displayMetrics.density).toInt()

        // Circular background dial using GradientDrawable
        val dialBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC222B29"))
            setStroke((2 * context.resources.displayMetrics.density).toInt(), Color.parseColor("#4D006C5F"))
        }

        val dialView = FrameLayout(context).apply {
            background = dialBg
            layoutParams = FrameLayout.LayoutParams(dialSizePx, dialSizePx)
        }

        val knobBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#006C5F"))
            setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.WHITE)
        }

        val knob = ImageView(context).apply {
            background = knobBg
            layoutParams = FrameLayout.LayoutParams(knobSizePx, knobSizePx).apply {
                gravity = Gravity.CENTER
            }
        }
        dialView.addView(knob)
        root.addView(dialView)

        val radius = dialSizePx / 2f

        // Touch handling
        dialView.setOnTouchListener { _, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - radius).toDouble()
                    val dy = (event.y - radius).toDouble()
                    val dist = hypot(dx, dy)
                    val radiusD = radius.toDouble()
                    val intensity = (dist / radiusD).coerceIn(0.0, 1.0).toFloat()

                    val angleDeg = (Math.toDegrees(atan2(dx, -dy)) + 360.0) % 360.0
                    val clampedDist = min(dist, radiusD)
                    val knobX = (clampedDist * sin(Math.toRadians(angleDeg))).toFloat()
                    val knobY = (-clampedDist * cos(Math.toRadians(angleDeg))).toFloat()

                    knob.translationX = knobX
                    knob.translationY = knobY

                    RouteSimulationService.engine.updateManualInput(angleDeg.toFloat(), intensity)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    knob.translationX = 0f
                    knob.translationY = 0f
                    RouteSimulationService.engine.updateManualInput(0f, 0f)
                    true
                }
                else -> false
            }
        }

        overlayView = root
        windowManager.addView(root, lp)
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
