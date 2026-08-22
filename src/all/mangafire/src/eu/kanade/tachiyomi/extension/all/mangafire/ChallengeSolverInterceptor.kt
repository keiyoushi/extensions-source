package eu.kanade.tachiyomi.extension.all.mangafire

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.getValue

object ChallengeSolverInterceptor : Interceptor {
    private val application by injectLazy<Application>()
    private val html by lazy { javaClass.getResource("/assets/solver.html")!!.readText() }

    @Serializable
    private data class ErrorResponse(
        val error: String?,
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (
            response.code != 403 ||
            response.peekBody(Long.MAX_VALUE).byteStream().parseAs<ErrorResponse>().error != "captcha_required"
        ) {
            return response
        }

        response.close()

        // We are solving the challenge in a WebView instead of directly in Kotlin because the solver depends on OpenCV, which is >100 MB
        // as a Kotlin dependency. Also, the OpenCV binaries would be in the storage of the extension app, making them inaccessible to the
        // reader app.
        // Using a WebView instead makes it possible to dynamically request OpenCV.js, keeping the app size small.

        val handler = Handler(Looper.getMainLooper())
        val latch = object : CountDownLatch(1) {
            @JavascriptInterface
            override fun countDown() {
                super.countDown()
            }
        }
        var webView: WebView? = null

        handler.post {
            Toast.makeText(application, "Attempting to solve MangaFire challenge", Toast.LENGTH_SHORT).show()

            val view = WebView(application)
            webView = view

            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                blockNetworkImage = false
                userAgentString = request.header("User-Agent")
            }

            // Somewhat useful if you need to debug WebView issues. Don't delete.
            /*view.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage == null) {
                        return false
                    }
                    val logContent = "wv: ${consoleMessage.message()} (${consoleMessage.sourceId()}, line ${consoleMessage.lineNumber()})"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.DEBUG -> Log.d("mangafire", logContent)
                        ConsoleMessage.MessageLevel.ERROR -> Log.e("mangafire", logContent)
                        ConsoleMessage.MessageLevel.LOG -> Log.i("mangafire", logContent)
                        ConsoleMessage.MessageLevel.TIP -> Log.i("mangafire", logContent)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("mangafire", logContent)
                        else -> Log.d("mangafire", logContent)
                    }

                    return true
                }
            }*/

            view.addJavascriptInterface(latch, "latch")

            view.loadDataWithBaseURL(
                "https://mangafire.to/@waf/solver",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        }

        latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        return chain.proceed(request)
    }
}
