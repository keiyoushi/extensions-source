package eu.kanade.tachiyomi.multisrc.catharsis

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
 * Swipes from ([fromX], [fromY]) to ([toX], [toY]) in CSS pixels at [scale] device pixels each, the way a finger does:
 * about half a second, starting slowly and tailing off, with a fingertip's contact. Stops when the run ends.
 */
internal fun WebViewScope<*>.swipe(scale: Float, fromX: Float, fromY: Float, toX: Float, toY: Float) {
    val frames = Random.nextInt(25, 37)
    val finger = Finger(scale)
    val handler = Handler(Looper.getMainLooper())

    fun send(frame: Int) {
        val action = when (frame) {
            0 -> MotionEvent.ACTION_DOWN
            frames + 1 -> MotionEvent.ACTION_UP
            else -> MotionEvent.ACTION_MOVE
        }
        val progress = 1 - (1 - (frame.coerceAtMost(frames).toFloat() / frames).pow(1.6f)).pow(4)
        finger.send(this, action, fromX + (toX - fromX) * progress, fromY + (toY - fromY) * progress)
        if (frame <= frames) handler.postDelayed({ send(frame + 1) }, Random.nextLong(14, 19))
    }
    whenDrawn { send(0) }
}

/**
 * Flicks from ([x], [fromY]) to ([x], [toY]) in CSS pixels at [scale] device pixels each and lets go while moving, so
 * the page keeps scrolling on its own, the way a reader skims a long chapter.
 */
internal fun WebViewScope<*>.fling(scale: Float, x: Float, fromY: Float, toY: Float) {
    val frames = Random.nextInt(6, 9)
    val finger = Finger(scale)
    val handler = Handler(Looper.getMainLooper())

    fun send(frame: Int) {
        val action = when (frame) {
            0 -> MotionEvent.ACTION_DOWN
            frames + 1 -> MotionEvent.ACTION_UP
            else -> MotionEvent.ACTION_MOVE
        }
        // Speeding up until the finger lifts
        val progress = (frame.coerceAtMost(frames).toFloat() / frames).pow(1.4f)
        finger.send(this, action, x, fromY + (toY - fromY) * progress)
        if (frame <= frames) handler.postDelayed({ send(frame + 1) }, Random.nextLong(14, 19))
    }
    whenDrawn { send(0) }
}

/** Taps ([x], [y]) in CSS pixels at [scale] device pixels each, the way a finger does: a short press that barely moves. */
internal fun WebViewScope<*>.tap(scale: Float, x: Float, y: Float) {
    val finger = Finger(scale)
    val handler = Handler(Looper.getMainLooper())
    val moves = Random.nextInt(0, 3)
    val stepMs = Random.nextLong(70, 130) / (moves + 1)

    fun send(frame: Int) {
        val action = when (frame) {
            0 -> MotionEvent.ACTION_DOWN
            moves + 1 -> MotionEvent.ACTION_UP
            else -> MotionEvent.ACTION_MOVE
        }

        // A pressed fingertip rolls a pixel or so
        fun roll() = if (action == MotionEvent.ACTION_MOVE) Random.nextDouble(-1.0, 1.0).toFloat() else 0f
        finger.send(this, action, x + roll(), y + roll())
        if (frame <= moves) handler.postDelayed({ send(frame + 1) }, stepMs)
    }
    whenDrawn { send(0) }
}

/** One fingertip's contact, sent to the WebView as touchscreen events. */
private class Finger(private val scale: Float) {
    private val majorMm = Random.nextDouble(7.0, 10.5).toFloat()
    private val minorRatio = Random.nextDouble(0.6, 0.95).toFloat()
    private val pressure = Random.nextDouble(0.25, 0.55).toFloat()
    private val orientation = Random.nextDouble(-PI / 2, PI / 2).toFloat()
    private var downTime = 0L

    @OptIn(NativeWebViewApi::class)
    fun send(scope: WebViewScope<*>, action: Int, x: Float, y: Float) = scope.withNativeWebView { view ->
        val metrics = view.resources.displayMetrics
        // Some vendors report a bogus xdpi; fall back to the density bucket then
        val pxPerMm = (metrics.xdpi.takeIf { it in 100f..1000f } ?: metrics.densityDpi.toFloat()) / 25.4f
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        // Contact and pressure shift a little while the finger moves
        fun drift(spread: Double) = if (action == MotionEvent.ACTION_MOVE) Random.nextDouble(1 - spread, 1 + spread).toFloat() else 1f
        // A real first touch has pointer id 0
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x * scale
            this.y = y * scale
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
    }
}

// Touches sent before the page has drawn are dropped, so wait up to a second for its next frame
@OptIn(NativeWebViewApi::class)
private fun WebViewScope<*>.whenDrawn(block: () -> Unit) = withNativeWebView { view ->
    val handler = Handler(Looper.getMainLooper())
    var started = false
    val start = Runnable {
        if (!started) {
            started = true
            block()
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

/** Runs [block] on the main thread after [ms]. */
internal fun later(ms: Long, block: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed(block, ms)
}

/**
 * Taps [point] (CSS pixels on a [width] x [height] screen at [scale]) once nothing covers it; else moves it towards the
 * upper part of the screen, with a flick when it is several screens away.
 */
fun <T> WebViewScope<T>.touch(scale: Float, width: Float, height: Float, point: Point): Step<T> = Step.Act { done ->
    val travel = point.y - height * 0.3f
    when {
        point.clear -> {
            tap(scale, point.x, point.y)
            later(TAP_WAIT_MS, done)
        }
        point.fromX == null && travel > height * FLING_SCREENS -> {
            fling(scale, width * 0.2f, height * 0.85f, height * 0.25f)
            later(FLING_WAIT_MS, done)
        }
        point.fromX != null && point.fromY != null -> {
            val distance = (point.y - point.fromY).coerceIn(-height * 0.3f, height * 0.3f)
            swipe(scale, point.fromX, point.fromY, point.fromX, point.fromY - distance)
            later(SWIPE_WAIT_MS, done)
        }
        else -> {
            val distance = travel.coerceIn(-height * 0.7f, height * 0.7f)
            val from = if (distance > 0) height * 0.85f else height * 0.15f
            swipe(scale, width * 0.2f, from, width * 0.2f, from - distance)
            later(SWIPE_WAIT_MS, done)
        }
    }
}

private const val TAP_WAIT_MS = 1200L
internal const val SWIPE_WAIT_MS = 900L
internal const val FLING_WAIT_MS = 1600L
internal const val FLING_SCREENS = 2.5f
