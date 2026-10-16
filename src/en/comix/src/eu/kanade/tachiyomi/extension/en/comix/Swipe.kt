package eu.kanade.tachiyomi.extension.en.comix

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.WebViewScope
import kotlin.math.PI
import kotlin.math.pow
import kotlin.random.Random

/**
 * Swipes from ([fromX], [fromY]) to ([toX], [toY]) in CSS pixels the way a finger does: about half a second,
 * starting slowly and tailing off, with a fingertip's contact. Stops when the run ends.
 */
@OptIn(NativeWebViewApi::class)
internal fun WebViewScope<*>.swipe(fromX: Float, fromY: Float, toX: Float, toY: Float) {
    val frames = Random.nextInt(25, 37)
    val majorMm = Random.nextDouble(7.0, 10.5).toFloat()
    val minorRatio = Random.nextDouble(0.6, 0.95).toFloat()
    val pressure = Random.nextDouble(0.25, 0.55).toFloat()
    val orientation = Random.nextDouble(-PI / 2, PI / 2).toFloat()
    val handler = Handler(Looper.getMainLooper())
    var downTime = 0L

    fun send(frame: Int) {
        withNativeWebView { view ->
            val metrics = view.resources.displayMetrics
            // Some vendors report a bogus xdpi; fall back to the density bucket then
            val pxPerMm = (metrics.xdpi.takeIf { it in 100f..1000f } ?: metrics.densityDpi.toFloat()) / 25.4f
            val action = when (frame) {
                0 -> MotionEvent.ACTION_DOWN
                frames + 1 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            if (frame == 0) downTime = SystemClock.uptimeMillis()
            // Contact and pressure shift a little while the finger moves
            fun drift(spread: Double) = if (action == MotionEvent.ACTION_MOVE) Random.nextDouble(1 - spread, 1 + spread).toFloat() else 1f
            val progress = 1 - (1 - (frame.coerceAtMost(frames).toFloat() / frames).pow(1.6f)).pow(4)
            val properties = MotionEvent.PointerProperties().apply { toolType = MotionEvent.TOOL_TYPE_FINGER }
            val coords = MotionEvent.PointerCoords().apply {
                x = (fromX + (toX - fromX) * progress) * metrics.density
                y = (fromY + (toY - fromY) * progress) * metrics.density
                this.pressure = (pressure * drift(0.25)).coerceIn(0.05f, 1f)
                touchMajor = majorMm * pxPerMm * drift(0.15)
                touchMinor = (majorMm * minorRatio * pxPerMm * drift(0.15)).coerceAtMost(touchMajor)
                toolMajor = touchMajor
                toolMinor = touchMinor
                this.orientation = orientation
                size = touchMajor / maxOf(metrics.widthPixels, metrics.heightPixels)
            }
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            try {
                view.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
            if (frame <= frames) handler.postDelayed({ send(frame + 1) }, Random.nextLong(14, 19))
        }
    }
    // Touches sent before the page has drawn are dropped, so wait up to a second for its next frame
    withNativeWebView { view ->
        var started = false
        val start = Runnable {
            if (!started) {
                started = true
                send(0)
            }
        }
        handler.postDelayed(start, 1000)
        view.postVisualStateCallback(
            0,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    handler.removeCallbacks(start)
                    start.run()
                }
            },
        )
    }
}
