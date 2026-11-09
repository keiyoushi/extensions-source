package keiyoushi.webview.internal

import keiyoushi.network.Fetch
import keiyoushi.network.RateLimitInterceptor
import keiyoushi.network.SentByBrowser
import keiyoushi.network.kind
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.function.Function

/**
 * How a route sends what WebView asked for: through [interceptor], then [client], minus the `X-Requested-With`
 * WebView adds for the app, tagged as a browser's request with the [Fetch.Kind] of what it loads. The
 * [WebViewProxy] only frames requests; it runs this in whichever copy of core serves, so these rules are always the
 * route holder's own.
 */
internal fun routeSend(client: OkHttpClient, interceptor: Interceptor?): Function<Request, Response> {
    // WebView follows redirects, keeps its own cookies and cache, paces its loads and shows the site's challenges
    val routeClient = client.newBuilder()
        .apply {
            // The app's, by name as in KeiSource
            interceptors().removeAll {
                it.javaClass.simpleName == "CloudflareInterceptor" || it is RateLimitInterceptor.TaggingInterceptor
            }
            networkInterceptors().removeAll { it is RateLimitInterceptor }
            if (interceptor != null) interceptors().add(0, interceptor)
        }
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .build()
    return Function { request ->
        val sent = request.newBuilder()
            .apply { if (request.headers(REQUESTED_WITH).any(PACKAGE_NAME::matches)) removeHeader(REQUESTED_WITH) }
            .kind(kindOf(request.headers))
            .tag(SentByBrowser::class.java, SentByBrowser)
            .build()
        routeClient.newCall(sent).execute()
    }
}

private const val REQUESTED_WITH = "X-Requested-With"

// Chromium adds a package name only where the page set no value: the app's, or the one apps such as Mihon have it say
// (Chrome's, else another installed app's). A page's own value, such as jQuery's XMLHttpRequest, goes out as in Chrome
private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

/** The core's [Fetch.Kind] for a request's Fetch metadata: the order its headers go out in and its Priority. */
private fun kindOf(headers: Headers): Fetch.Kind = when (headers["Sec-Fetch-Dest"]?.lowercase(Locale.ROOT)) {
    "document", "iframe", "frame" -> Fetch.Kind.DOCUMENT
    "image" -> Fetch.Kind.IMAGE
    "empty" -> Fetch.Kind.XHR
    null -> if (headers["Sec-Fetch-Mode"] == "navigate") Fetch.Kind.DOCUMENT else Fetch.Kind.XHR
    else -> Fetch.Kind.SCRIPT
}
