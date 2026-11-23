package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.app.Presentation
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

internal object Surface {
    fun host(webView: WebView, width: Int, height: Int) {
        val frames = HandlerThread("catharsis-webview-frames").apply { start() }
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(frames.looper))
        val displayManager = webView.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.createVirtualDisplay(
            "catharsis-webview",
            width,
            height,
            webView.resources.displayMetrics.densityDpi,
            reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        )
        if (display == null) {
            reader.close()
            frames.quitSafely()
            error("No hay una pantalla virtual disponible para el WebView")
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        val presentation = Presentation(webView.context.applicationContext, display.display).apply {
            setContentView(webView, ViewGroup.LayoutParams(width, height))
            show()
        }
        (webView.parent as ViewGroup).setOnHierarchyChangeListener(
            object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {}

                override fun onChildViewRemoved(parent: View, child: View) {
                    if (child !== webView) return
                    (parent as ViewGroup).setOnHierarchyChangeListener(null)
                    presentation.dismiss()
                    display.release()
                    reader.close()
                    frames.quitSafely()
                }
            },
        )
    }
}
