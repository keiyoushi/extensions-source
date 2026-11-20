package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.WebViewScope

// The site turns off click() and focus() in JavaScript, so its buttons and search field are reached with real
// input events. A touch sent before the page has drawn a frame is dropped, so it waits for one.
@OptIn(NativeWebViewApi::class)
internal fun WebViewScope<*>.tap(x: Float, y: Float) = withNativeWebView { view ->
    val handler = Handler(Looper.getMainLooper())
    var sent = false
    val press = Runnable {
        if (sent) return@Runnable
        sent = true
        val density = view.resources.displayMetrics.density
        val downTime = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to downTime, MotionEvent.ACTION_UP to downTime + 60)) {
            val event = MotionEvent.obtain(downTime, time, action, x * density, y * density, 0)
            try {
                view.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }
    handler.postDelayed(press, 1000)
    view.postVisualStateCallback(
        0,
        object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                handler.removeCallbacks(press)
                press.run()
            }
        },
    )
}

@OptIn(NativeWebViewApi::class)
internal fun WebViewScope<*>.typeText(text: String) = withNativeWebView { view ->
    KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())?.forEach { view.dispatchKeyEvent(it) }
    view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
    view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
}
