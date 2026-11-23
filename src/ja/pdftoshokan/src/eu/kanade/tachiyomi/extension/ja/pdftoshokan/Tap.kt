package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.WebViewScope

/** Taps ([x], [y]) in CSS pixels with a fingertip's touchscreen events, which the page sees as a trusted click. */
@NativeWebViewApi
internal fun WebViewScope<*>.tap(x: Float, y: Float) {
    val handler = Handler(Looper.getMainLooper())
    var downTime = 0L

    fun send(action: Int) = withNativeWebView { view ->
        val density = view.resources.displayMetrics.density
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x * density
            this.y = y * density
            pressure = 0.4f
            size = 0.05f
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

    // Touches sent before the page has drawn are dropped, so wait for its next frame
    withNativeWebView { view ->
        view.postVisualStateCallback(
            0,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    send(MotionEvent.ACTION_DOWN)
                    handler.postDelayed({ send(MotionEvent.ACTION_UP) }, 100)
                }
            },
        )
    }
}
