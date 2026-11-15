package eu.kanade.tachiyomi.multisrc.pam

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

/** Shows an offscreen [WebView] on a private virtual display, so it renders like one on screen, and reads back its frames. */
internal class Surface private constructor(private val webView: WebView) {
    private val frameThread = HandlerThread("pam-webview-frames").apply { start() }
    private val frameHandler = Handler(frameThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private class Screen(val reader: ImageReader, val display: VirtualDisplay, val presentation: Presentation)

    private var screen: Screen? = null
    private var moving = false

    // Frame thread only
    private var latest: Image? = null
    private var frames = 0L

    var width = 0
        private set

    /** Moves the WebView to a new [width] x [height] display; a presentation closes when its display is resized. Main thread. */
    fun show(width: Int, height: Int) {
        val old = screen
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ onFrame(it) }, frameHandler)
        val displayManager = webView.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.createVirtualDisplay(
            "pam-webview",
            width,
            height,
            webView.resources.displayMetrics.densityDpi,
            reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        ) ?: run {
            reader.close()
            error("No virtual display available for the WebView")
        }
        moving = true
        (webView.parent as? ViewGroup)?.removeView(webView)
        moving = false
        val presentation = Presentation(webView.context.applicationContext, display.display).apply {
            setContentView(webView, ViewGroup.LayoutParams(width, height))
            show()
        }
        (webView.parent as ViewGroup).setOnHierarchyChangeListener(
            object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {}

                override fun onChildViewRemoved(parent: View, child: View) {
                    if (child === webView && !moving) release()
                }
            },
        )
        screen = Screen(reader, display, presentation)
        this.width = width
        old?.close()
    }

    private fun Screen.close() {
        presentation.dismiss()
        display.release()
        reader.setOnImageAvailableListener(null, null)
        frameHandler.post {
            latest?.close()
            latest = null
            reader.close()
        }
    }

    // The held frame takes one of the reader's two buffers, so it goes before the next one can be taken
    private fun onFrame(reader: ImageReader) {
        latest?.close()
        latest = reader.acquireLatestImage()?.also { frames++ }
    }

    /**
     * Copies rows [top] until [bottom] and columns [left] until [right] (frame pixels) into a new bitmap once two frames in
     * a row agree there and it shows no unpainted stretch, then calls [done] on the main thread with it, or null if it can't.
     */
    fun copy(left: Int, top: Int, right: Int, bottom: Int, done: (Bitmap?) -> Unit) {
        fun finish(segment: Bitmap?) = mainHandler.post { done(segment) }
        fun attempt(round: Int, previous: Long?) {
            val after = frames
            mainHandler.post { webView.invalidate() }
            fun read(poll: Int) {
                if (frames == after && poll < FRAME_POLLS) {
                    frameHandler.postDelayed({ read(poll + 1) }, FRAME_POLL_MS)
                    return
                }
                val image = latest
                if (image == null || left < 0 || top < 0 || right > image.width || bottom > image.height || bottom <= top) {
                    finish(null)
                    return
                }
                val print = image.fingerprint(left, top, right, bottom)
                // Tiles still being painted after a scroll show as black and change from frame to frame
                val settled = print == previous && !image.hasBlankStretch(left, top, right, bottom)
                if (!settled && round < SETTLE_ROUNDS) {
                    frameHandler.postDelayed({ attempt(round + 1, print) }, SETTLE_MS)
                    return
                }
                finish(image.copyRows(left, top, right, bottom))
            }
            read(0)
        }
        frameHandler.post { attempt(0, null) }
    }

    /** A sample of the region's pixels, to tell whether two frames show the same thing there. */
    private fun Image.fingerprint(left: Int, top: Int, right: Int, bottom: Int): Long {
        val plane = planes[0]
        val buffer = plane.buffer
        var hash = 1125899906842597L
        for (y in top until bottom step 3) {
            for (x in left until right step 7) {
                hash = 31 * hash + buffer.getInt(y * plane.rowStride + x * plane.pixelStride)
            }
        }
        return hash
    }

    /** Whether the region has a long run of rows that are entirely black. */
    private fun Image.hasBlankStretch(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val plane = planes[0]
        val buffer = plane.buffer
        var run = 0
        for (y in top until bottom) {
            var blank = true
            for (x in left until right step 5) {
                val at = y * plane.rowStride + x * plane.pixelStride
                // The colour bytes, whatever order an int would read them in
                if ((buffer.get(at).toInt() or buffer.get(at + 1).toInt() or buffer.get(at + 2).toInt()) and 0xF0 != 0) {
                    blank = false
                    break
                }
            }
            run = if (blank) run + 1 else 0
            if (run >= BLANK_ROWS) return true
        }
        return false
    }

    private fun Image.copyRows(left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer.duplicate().apply {
            position(top * plane.rowStride)
            limit(bottom * plane.rowStride)
        }.slice()
        val rows = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, bottom - top, Bitmap.Config.ARGB_8888)
        rows.copyPixelsFromBuffer(buffer)
        val segment = Bitmap.createBitmap(rows, left, 0, right - left, bottom - top)
        if (segment !== rows) rows.recycle()
        return segment
    }

    private fun release() {
        screen?.close()
        screen = null
        frameThread.quitSafely()
    }

    companion object {
        /** Shows [webView] on a [width] x [height] display until it leaves it. Main thread. */
        fun host(webView: WebView, width: Int, height: Int): Surface = Surface(webView).apply {
            try {
                show(width, height)
            } catch (e: Throwable) {
                frameThread.quitSafely()
                throw e
            }
        }
    }
}

private const val FRAME_POLL_MS = 20L
private const val FRAME_POLLS = 8
private const val SETTLE_ROUNDS = 30
private const val SETTLE_MS = 100L

// Unpainted tiles are at least this tall; a black panel this tall is copied after the last round anyway
private const val BLANK_ROWS = 48
