package eu.kanade.tachiyomi.extension.en.bookwalker

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.extension.en.bookwalker.BookWalkerChapterReader.ImageResult.NotReady.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

class BookWalkerChapterReader(val readerUrl: String, private val prefs: BookWalkerPreferences) {

    /**
     * For when the reader is working properly but there's still an error fetching data.
     * Currently this is only used when trying to fetch past the available portion of a preview.
     */
    class NonFatalReaderException(message: String) : Exception(message)

    private val app by injectLazy<Application>()

    // We need to be careful to avoid CoroutineScope.launch since that will disconnect thrown
    // exceptions inside the launched code from the calling coroutine and cause the entire
    // application to crash rather than just showing an error when an image fails to load.

    // WebView interaction _must_ occur on the UI thread, but everything else should happen on an
    // IO thread to avoid stalling the application.

    private fun <T> evaluateOnUiThread(block: suspend CoroutineScope.() -> T): Deferred<T> = CoroutineScope(Dispatchers.Main.immediate).async { block() }

    private fun <T> evaluateOnIOThread(block: suspend CoroutineScope.() -> T): Deferred<T> = CoroutineScope(Dispatchers.IO).async { block() }

    private val isDestroyed = MutableStateFlow(false)

    /**
     * Calls [block] with the reader's active WebView as its argument and returns the result.
     * [block] is guaranteed to run on a thread that can safely interact with the WebView.
     */
    private suspend fun <T> usingWebView(block: suspend (webview: WebView) -> T): T {
        if (isDestroyed.value) {
            throw Exception("Reader was destroyed")
        }
        return webview.await().let {
            evaluateOnUiThread {
                block(it)
            }.await()
        }
    }

    private val webview = evaluateOnUiThread {
        suspendCoroutine { cont ->
            var cancelled = false
            val timer = Timer().schedule(WEBVIEW_STARTUP_TIMEOUT.inWholeMilliseconds) {
                // Don't destroy the webview here, that's the responsibility of the caller.
                cancelled = true
                cont.resumeWithException(Exception("WebView didn't load within $WEBVIEW_STARTUP_TIMEOUT"))
            }

            Log.d("bookwalker", "Creating Webview...")
            WebView(app).apply {
                // The aspect ratio needs to be thinner than 3:2 to avoid two pages rendering at
                // once, which would break the image-fetching logic. 1:1 works fine.
                // We grab the image before it gets resized to fit the viewport so the image size
                // doesn't directly correlate with screen size, but the size of the screen still
                // affects the size of source image that the reader tries to render.
                // The available resolutions vary per series, but in general, the largest resolution
                // is typically on the order of 2k pixels vertical, with reduced-resolution variants
                // on each factor of two (1/2, 1/4, etc.) for smaller screens.
                val size = when (prefs.imageQuality) {
                    ImageQualityPref.DEVICE -> max(
                        app.resources.displayMetrics.heightPixels,
                        app.resources.displayMetrics.widthPixels,
                    )

                    // "Medium" doesn't necessarily mean we'll use the 1/2x variant, just that we'll
                    // use the variant that BookWalker thinks is appropriate for a 1000px screen
                    // (which typically is the 1/2x variant for manga with high native resolutions).
                    ImageQualityPref.MEDIUM -> 1000

                    // A 2000x2000px WebView consistently captured the largest variant in testing,
                    // but just in case some series can have a higher max resolution, 3000px is used
                    // for the "high" image quality option. In theory we could go higher (like 10k)
                    // and it wouldn't affect the image size, but there start to be performance
                    // issues when the BookWalker viewer tries to draw onto huge canvases.
                    ImageQualityPref.HIGH -> 3000
                }
                Log.d("bookwalker", "WebView size $size")
                // Note: The BookWalker viewer is DPI-aware, so even though the innerWidth/Height
                // values in JavaScript may not match the layout() call, everything works properly.
                layout(0, 0, size, size)

                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Mobile vs desktop doesn't matter much, but the mobile layout has a longer page
                // slider which allows for more accuracy when trying to jump to a particular page.
                settings.userAgentString = USER_AGENT_MOBILE

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        timer.cancel()

                        if (cancelled) {
                            Log.d("bookwalker", "WebView loaded $url after being destroyed")
                            return
                        }

                        Log.d("bookwalker", "WebView loaded $url")

                        if (url.contains("member.bookwalker.jp")) {
                            cont.resumeWithException(Exception("Logged out, check website in WebView"))
                            return
                        }

                        cont.resume(view)
                    }
                }

//                webChromeClient = object : WebChromeClient() {
//                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
//                        Log.println(
//                            when (consoleMessage.messageLevel()!!) {
//                                ConsoleMessage.MessageLevel.TIP -> Log.VERBOSE
//                                ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
//                                ConsoleMessage.MessageLevel.LOG -> Log.INFO
//                                ConsoleMessage.MessageLevel.WARNING -> Log.WARN
//                                ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
//                            },
//                            "bookwalker.console",
//                            "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}",
//                        )
//
//                        return super.onConsoleMessage(consoleMessage)
//                    }
//                }

                this.addJavascriptInterface(jsInterface, INTERFACE_NAME)

                // Adding the below line makes a console error go away, but it doesn't seem to affect functionality.
                // webview.addJavascriptInterface(object {}, "Notification")

                loadUrl(readerUrl)
            }
        }.also {
            it.evaluateJavascript(
                injectionScriptReplacements.asIterable()
                    .fold(webviewInjectionScript) { script, replacement ->
                        script.replace(replacement.key, replacement.value)
                    },
                null,
            )
        }
    }

    private suspend fun evaluateJavascript(script: String): String? = usingWebView { webview ->
        suspendCoroutine { cont ->
            webview.evaluateJavascript(script) {
                cont.resume(it)
            }
        }
    }

    suspend fun destroy() {
        Log.d("bookwalker", "Destroy called")
        try {
            usingWebView {
                it.destroy()
                isDestroyed.value = true
            }
        } catch (e: Exception) {
            // OK, the webview was probably already destroyed
//            Log.d("bookwalker", "Destroy error: $e")
        }
    }

    /**
     * Returns a flow which transparently forwards the original flow except that if the WebView is
     * destroyed while waiting for data (or was already destroyed), suspending calls to obtain the
     * data will throw.
     *
     * If the WebView was already destroyed when the suspending call was made but data from the
     * original flow is immediately received, it is not deterministic whether it will return the
     * data or throw.
     */
    private fun <T> Flow<T>.throwOnDestroyed(): Flow<T> = merge(
        this.map { false to it },
        isDestroyed.filter { it }.map { true to null },
    ).map {
        if (it.first) {
            throw Exception("Reader was destroyed")
        }
        // Can't use !! here because T might be nullable
        @Suppress("UNCHECKED_CAST")
        it.second as T
    }

    private val isViewerReady = MutableStateFlow(false)

    private suspend fun waitForViewer() {
        webview.await()
        isViewerReady.filter { it }.throwOnDestroyed().first()
    }

    private sealed class ImageResult {
        object NotReady : ImageResult()
        class Found(val data: Deferred<ByteArray>) : ImageResult()
        class NotFound(val error: Throwable) : ImageResult()

        suspend fun Flow<ImageResult>.get(): ByteArray {
            @OptIn(FlowPreview::class)
            return flatMapConcat {
                when (it) {
                    is NotReady -> emptyFlow()
                    is Found -> flow<ByteArray> { emit(it.data.await()) }
                    is NotFound -> throw it.error
                }
            }.first()
        }
    }

    private val imagesMap = mutableMapOf<Int, MutableStateFlow<ImageResult>>()

    private val navigationMutex = Mutex()

    /**
     * Retrieves JPEG image data for the requested page (0-indexed)
     */
    suspend fun getPage(index: Int): ByteArray {
        val state = synchronized(imagesMap) {
            imagesMap.getOrPut(index) { MutableStateFlow(ImageResult.NotReady) }
        }

        waitForViewer()

        // Attempting to fetch two pages concurrently doesn't work.
        val imgData = navigationMutex.withLock {
            evaluateJavascript("(() => $JS_UTILS_NAME.fetchPageData($index))()")

            val result = withTimeoutOrNull(IMAGE_FETCH_TIMEOUT) {
                Log.d("bookwalker", "Waiting on image index $index ($state)")
                state.throwOnDestroyed().get()
            } ?: throw Exception("Timed out waiting for image $index to load")

            // Stop holding onto the image in the map so it can get collected. Due to caching by the
            // app, it is unlikely that the same reader will need to fetch the same image twice, and
            // even if it does, the image may still be stored in memory at the webview level.
            state.value = ImageResult.NotReady

            result
        }

        Log.d("bookwalker", "Retrieved data for image $index (${imgData.size} bytes)")
        return imgData
    }

    private val jsInterface = object {
        @JavascriptInterface
        fun reportViewerLoaded() {
            Log.d("bookwalker", "Viewer loaded")
            isViewerReady.value = true
        }

        @JavascriptInterface
        fun reportFailedToLoad(message: String) {
            Log.e("bookwalker", "Failed to load BookWalker viewer: $message")
            // launch should be safe here, destroy() should not throw (except for truly critical errors)
            CoroutineScope(Dispatchers.IO).launch { destroy() }
        }

        @JavascriptInterface
        fun reportImage(index: Int, imageDataAsString: String, width: Int, height: Int) {
            // Byte arrays cannot be directly transferred efficiently, so strings are our
            // best choice for transporting large images out of the Webview.
            // See https://stackoverflow.com/a/45506857
            val data = imageDataAsString.toByteArray(Charset.forName("windows-1252"))
            Log.d("bookwalker", "received image $index (${data.size} bytes, ${width}x$height)")

            val state = synchronized(imagesMap) {
                imagesMap.getOrPut(index) { MutableStateFlow(ImageResult.NotReady) }
            }

            // The raw bitmap data is very large, so we want to compress it down as soon as possible
            // and store that rather than keeping uncompressed images in memory.
            state.value = ImageResult.Found(
                evaluateOnIOThread {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN))

                    ByteArrayOutputStream().apply {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, this)
                    }.toByteArray()
                },
            )
        }

        @JavascriptInterface
        fun reportImageDoesNotExist(index: Int, reason: String) {
            val state = synchronized(imagesMap) {
                imagesMap.getOrPut(index) { MutableStateFlow(ImageResult.NotReady) }
            }

            state.value = ImageResult.NotFound(NonFatalReaderException(reason))
        }
    }

    companion object {
        private val webviewInjectionScript by lazy {
            this::class.java.getResource("/assets/webview-script.js")?.readText()
                ?: throw Error("Failed to retrieve webview injection script")
        }

        private const val INTERFACE_NAME = "BOOKWALKER_EXT_COMMS"
        private const val JS_UTILS_NAME = "BOOKWALKER_EXT_UTILS"

        private val injectionScriptReplacements = mapOf(
            "__INJECT_WEBVIEW_INTERFACE" to INTERFACE_NAME,
            "__INJECT_JS_UTILITIES" to JS_UTILS_NAME,
        )

        // Sometimes the webview just fails to load for some reason and we need to retry, so this
        // timeout should be kept as short as possible. 15 seconds seems like a decent upper bound.
        private val WEBVIEW_STARTUP_TIMEOUT = 15.seconds

        // Images can take a while to load especially if the viewer is in a poor location and needs
        // to track to a completely different part of the chapter, but if it's been 15 seconds
        // since an image was requested with no response, it usually means something is broken.
        // Note that the image fetch timer only starts after the webview loads.
        private val IMAGE_FETCH_TIMEOUT = 15.seconds
    }
}
