package keiyoushi.network

import android.os.LocaleList
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

/**
 * Starts a call's [BrowserCall], which [BrowserHeadersInterceptor] works from on each hop: for a request with a
 * [Fetch.from] page, or one of a [Fetch.Kind] with a `User-Agent` naming Chrome or Firefox. The covers the apps load
 * go out with [imageFetch].
 */
internal class BrowserCallInterceptor(
    private val siteUrl: () -> String,
    private val imageFetch: () -> Fetch,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // The apps' cover requests carry no Fetch, only the no-store that keeps covers out of the OkHttp cache
        val request = chain.request().let {
            val cover = it.kind == null && it.method == "GET" && it.header("Cache-Control") == "no-store"
            if (cover) it.newBuilder().fetch(imageFetch()).build() else it
        }.let {
            // A retry built from a response's request brings the last call's tag
            if (it.tag(BrowserCall::class.java) != null) it.newBuilder().tag(BrowserCall::class.java, null).build() else it
        }
        val kind = request.kind
        val from = request.tag(FromPage::class.java)
        // Nothing to do, and baseUrl stays unread
        if (from == null && (kind == null || kind == Fetch.Kind.NONE)) return chain.proceed(request)
        val siteUrl = siteUrl()
        val site = siteUrl.toHttpUrlOrNull()
        val page = from?.let { it.page.toHttpUrlOrNull() ?: site?.resolve(it.page) }
        val browserHeaders = kind != null && kind != Fetch.Kind.NONE && site != null && Browser.of(request.header("User-Agent").orEmpty()) != null
        if (page == null && !browserHeaders) return chain.proceed(request)
        // A fresh one per call: a request or builder can be sent more than once
        val call = BrowserCall(page, site.takeIf { browserHeaders }, siteUrl)
        return chain.proceed(request.newBuilder().tag(BrowserCall::class.java, call).build())
    }
}

/**
 * A call's page and site, and what its redirect chain has passed through so far. [site] is set when the call gets
 * browser headers.
 */
internal class BrowserCall(val page: HttpUrl?, val site: HttpUrl?, val siteUrl: String) {
    // The page as last sent as Referer, trimmed further on each hop
    var referer: HttpUrl? = page

    // The least related site the chain has passed through, as browsers report it
    var fetchSite: String? = null

    // The origin of the previous hop, and whether the chain left it for another (Origin goes out as "null" then)
    var hopOrigin: String? = null
    var tainted = false
}

/**
 * Adds the headers Chrome or Firefox, as named in a request's `User-Agent`, sends for its [Fetch.Kind] and puts them in
 * that browser's order: client hints, `Accept-Language`, fetch metadata, `Accept` and `Priority`, and KeiSource's
 * default `Origin` only where browsers send one. Any other request goes out as built, apart from the `Referer` of
 * [Fetch.from].
 *
 * Runs per hop on the network level, so it sees `Cookie`, the body headers OkHttp adds and each redirect. Every hop
 * starts from the request as the source built it; headers it already has are kept.
 */
internal object BrowserHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val call = original.tag(BrowserCall::class.java) ?: return chain.proceed(original)
        val http2 = chain.connection()?.protocol() == Protocol.HTTP_2
        val response = chain.proceed(call.hop(original).inWireOrder(http2))
        return response.newBuilder()
            // The cache keeps the request as the source built it
            .request(original)
            // Every hop of a redirect chain goes over the network, where its headers are worked out
            .apply { if (response.isRedirect) header("Cache-Control", "no-store") }
            .build()
    }
}

/** The request with the headers the browser sends on this hop of the call. */
internal fun BrowserCall.hop(request: Request): Request {
    val url = request.url
    val builder = request.newBuilder()
    if (page != null) {
        referer = referer?.let { referrer(it, url) }
        builder.referer(referer)
    }
    val site = site ?: return builder.build()
    val kind = request.kind!!
    val userAgent = request.header("User-Agent").orEmpty()
    val browser = Browser.of(userAgent)!!
    val initiator = page ?: request.header("Referer")?.toHttpUrlOrNull() ?: site
    val origin = url.origin()
    hopOrigin?.let { previous -> if (previous != initiator.origin() && origin != previous) tainted = true }
    hopOrigin = origin
    fun fill(name: String, value: String?) {
        if (value != null && request.header(name) == null) builder.header(name, value)
    }

    // Browsers send fetch metadata and client hints to secure origins only
    val secure = url.isSecure()
    if (secure) browser.clientHints(userAgent).forEach { (name, value) -> fill(name, value) }
    fill("Accept-Language", acceptLanguage(deviceLocales()).ifEmpty { null })
    fill("Accept", browser.accept(kind, userAgent))
    if (secure) {
        if (request.header("Sec-Fetch-Site") == null) {
            fetchSite = listOfNotNull(fetchSite, relation(initiator, url)).maxBy(SITES::indexOf)
            builder.header("Sec-Fetch-Site", fetchSite!!)
        }
        fill(
            "Sec-Fetch-Mode",
            when (kind) {
                Fetch.Kind.DOCUMENT -> "navigate"
                Fetch.Kind.XHR -> "cors"
                else -> "no-cors"
            },
        )
        if (kind == Fetch.Kind.DOCUMENT) fill("Sec-Fetch-User", "?1")
        fill(
            "Sec-Fetch-Dest",
            when (kind) {
                Fetch.Kind.DOCUMENT -> "document"
                Fetch.Kind.XHR -> "empty"
                Fetch.Kind.IMAGE -> "image"
                Fetch.Kind.SCRIPT -> "script"
                Fetch.Kind.NONE -> error("Not sent with browser headers")
            },
        )
    }
    if (kind == Fetch.Kind.DOCUMENT) fill("Upgrade-Insecure-Requests", "1")
    fill("Priority", browser.priority(kind))
    // KeiSource's default Origin is ours to keep or drop; one the source set or removed itself stays
    if (request.header("Origin").let { it == siteUrl || it == site.origin() }) {
        // Browsers send Origin on methods other than GET and HEAD and on cross-origin XHRs
        val sendsOrigin = (request.method != "GET" && request.method != "HEAD") || (kind == Fetch.Kind.XHR && initiator.origin() != origin)
        if (sendsOrigin) builder.header("Origin", if (tainted) "null" else initiator.origin()) else builder.removeHeader("Origin")
    }
    return builder.build()
}

/**
 * The request in the order the browser in its `User-Agent` sends headers. It also drops what that browser doesn't send
 * (`Priority` over HTTP/1.1 in Chrome, the OkHttp cache's own `Cache-Control` directives) and adds Chrome's
 * `Cache-Control` on a form submit.
 */
internal fun Request.inWireOrder(http2: Boolean): Request {
    val kind = kind?.takeIf { it != Fetch.Kind.NONE } ?: return this
    val browser = Browser.of(header("User-Agent").orEmpty()) ?: return this
    val navigation = kind == Fetch.Kind.DOCUMENT
    val order = if (navigation) browser.documentOrder else browser.subresourceOrder
    val rank = { name: String -> order.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 } ?: order.indexOf(SITE) }
    val sorted = headers
        .filterNot { (name, value) -> name.equals("Cache-Control", ignoreCase = true) && value.isAppCacheDirective() }
        .filter { (name) -> http2 || browser.priorityOnHttp1 || !name.equals("Priority", ignoreCase = true) }
        .map { (name, value) ->
            // Pages send application/json without a charset (axios, fetch with the type set)
            val json = body != null && name.equals("Content-Type", ignoreCase = true)
            name to if (json) value.replace(JSON_CHARSET, "") else value
        }
        .plus(if (http2 && browser.sendsTeTrailers) listOf("te" to "trailers") else emptyList())
        .let { list ->
            val formSubmit = browser.formSubmitCacheControl?.takeIf { navigation && method == "POST" }
            val hasCacheControl = list.any { (name) -> name.equals("Cache-Control", ignoreCase = true) }
            if (formSubmit != null && !hasCacheControl) list + ("Cache-Control" to formSubmit) else list
        }
        // Stable: a site's own headers keep the order they were added in
        .sortedBy { (name) -> rank(name) }
    return newBuilder().headers(Headers.Builder().apply { sorted.forEach { (name, value) -> add(name, value) } }.build()).build()
}

private fun relation(initiator: HttpUrl, url: HttpUrl): String = when {
    initiator.origin() == url.origin() -> "same-origin"
    (initiator.registrableDomain() ?: initiator.host) == (url.registrableDomain() ?: url.host) -> "same-site"
    else -> "cross-site"
}

private val SITES = listOf("same-origin", "same-site", "cross-site")

// The page as browsers send it as Referer by default (strict-origin-when-cross-origin); null when they send none
private fun referrer(page: HttpUrl, url: HttpUrl): HttpUrl? {
    val sent = page.newBuilder().username("").password("").fragment(null)
    return when {
        page.isHttps && !url.isHttps -> null
        page.origin() == url.origin() -> sent.build()
        else -> sent.encodedPath("/").query(null).build()
    }
}

private fun Request.Builder.referer(sent: HttpUrl?) {
    if (sent == null) removeHeader("Referer") else header("Referer", sent.toString())
}

private fun HttpUrl.isSecure() = isHttps || host in LOCAL_HOSTS

/** A browser's values per request kind and its header order. */
internal sealed class Browser {
    abstract fun accept(kind: Fetch.Kind, userAgent: String): String
    abstract fun priority(kind: Fetch.Kind): String
    open fun clientHints(userAgent: String): Map<String, String> = emptyMap()

    // The orders measured over HTTP/2, [SITE] marking where a page's own headers go. Over HTTP/1.1 browsers also
    // move Connection and Cookie and change name case; matching that too would take a lot more rules for little gain
    abstract val documentOrder: List<String>
    abstract val subresourceOrder: List<String>
    open val priorityOnHttp1 = false
    open val sendsTeTrailers = false
    open val formSubmitCacheControl: String? = null

    // Chrome 155
    data object Chrome : Browser() {
        // image/jxl from Chrome 155
        override fun accept(kind: Fetch.Kind, userAgent: String): String {
            val jxl = if (chromeVersion(userAgent)!! >= 155) "image/jxl," else ""
            return when (kind) {
                Fetch.Kind.DOCUMENT ->
                    "text/html,application/xhtml+xml,application/xml;q=0.9,${jxl}image/avif,image/webp,image/apng,*/*;q=0.8," +
                        "application/signed-exchange;v=b3;q=0.7"
                Fetch.Kind.IMAGE -> "${jxl}image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                else -> "*/*"
            }
        }

        // Scripts as loaded from the page's head
        override fun priority(kind: Fetch.Kind) = when (kind) {
            Fetch.Kind.DOCUMENT -> "u=0, i"
            Fetch.Kind.IMAGE -> "i"
            Fetch.Kind.XHR -> "u=1, i"
            else -> "u=1"
        }

        override fun clientHints(userAgent: String): Map<String, String> {
            val version = chromeVersion(userAgent) ?: return emptyMap()
            return buildMap {
                brand(userAgent, version)?.let { (name, brandVersion) -> put("Sec-CH-UA", chromeBrands(version, name, brandVersion)) }
                put("Sec-CH-UA-Mobile", if ("Mobile" in userAgent) "?1" else "?0")
                put("Sec-CH-UA-Platform", "\"${platform(userAgent)}\"")
            }
        }

        // Other Chromium browsers put their own brand in Sec-CH-UA; the ones not listed are left without one
        private fun brand(userAgent: String, version: Int): Pair<String, Int>? = when {
            "; wv)" in userAgent -> "Android WebView" to version
            EDGE_VERSION_REGEX.containsMatchIn(userAgent) -> "Microsoft Edge" to EDGE_VERSION_REGEX.find(userAgent)!!.groupValues[1].toInt()
            CHROMIUM_FORKS.any { it in userAgent } -> null
            else -> "Google Chrome" to version
        }

        override val documentOrder = listOf(
            "Host", "Connection", "Content-Length", "Cache-Control", "Sec-CH-UA", "Sec-CH-UA-Mobile", "Sec-CH-UA-Platform",
            "Upgrade-Insecure-Requests", "Content-Type", "User-Agent", "Origin", SITE, "Accept", "Sec-Fetch-Site",
            "Sec-Fetch-Mode", "Sec-Fetch-User", "Sec-Fetch-Dest", "Referer", "Accept-Encoding", "Accept-Language", "Priority",
            "Cookie",
        )
        override val subresourceOrder = listOf(
            "Host", "Connection", "Content-Length", "Sec-CH-UA-Platform", "User-Agent", "Sec-CH-UA", "Content-Type",
            "Sec-CH-UA-Mobile", SITE, "Accept", "Origin", "Sec-Fetch-Site", "Sec-Fetch-Mode", "Sec-Fetch-Dest", "Referer",
            "Accept-Encoding", "Accept-Language", "Priority", "Cookie",
        )

        override val formSubmitCacheControl = "max-age=0"
    }

    // Firefox 158
    data object Firefox : Browser() {
        override fun accept(kind: Fetch.Kind, userAgent: String) = when (kind) {
            Fetch.Kind.DOCUMENT -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            Fetch.Kind.IMAGE -> "image/avif,image/jxl,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5"
            else -> "*/*"
        }

        // XHR as fetch() sends it (Firefox's XMLHttpRequest sends none); scripts from the page's head
        override fun priority(kind: Fetch.Kind) = when (kind) {
            Fetch.Kind.DOCUMENT -> "u=0, i"
            Fetch.Kind.IMAGE -> "u=5, i"
            Fetch.Kind.XHR -> "u=4"
            else -> "u=2"
        }

        override val documentOrder = listOf(
            "Host", "User-Agent", "Accept", "Accept-Language", "Accept-Encoding", "Content-Type", "Content-Length", "Origin",
            "Connection", "Referer", SITE, "Upgrade-Insecure-Requests", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site",
            "Sec-Fetch-User", "Priority", "TE", "Cookie",
        )
        override val subresourceOrder = listOf(
            "Host", "User-Agent", "Accept", "Accept-Language", "Accept-Encoding", "Connection", "Referer", SITE,
            "Content-Type", "Content-Length", "Origin", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site", "Priority",
            "TE", "Cookie",
        )
        override val priorityOnHttp1 = true
        override val sendsTeTrailers = true
    }

    companion object {
        fun of(userAgent: String): Browser? = when {
            chromeVersion(userAgent) != null -> Chrome
            FIREFOX_VERSION_REGEX.containsMatchIn(userAgent) -> Firefox
            else -> null
        }
    }
}

/** Chromium's `Sec-CH-UA` brand list for [version], its placeholder brand included. */
internal fun chromeBrands(version: Int, brand: String = "Google Chrome", brandVersion: Int = version): String {
    val brands = arrayOfNulls<String>(3)
    val order = BRAND_ORDERS[version % BRAND_ORDERS.size]
    val greaseChar = { offset: Int -> GREASE_CHARS[(version + offset) % GREASE_CHARS.size] }
    brands[order[0]] = "\"Not${greaseChar(0)}A${greaseChar(1)}Brand\";v=\"${GREASE_VERSIONS[version % GREASE_VERSIONS.size]}\""
    brands[order[1]] = "\"Chromium\";v=\"$version\""
    brands[order[2]] = "\"$brand\";v=\"$brandVersion\""
    return brands.joinToString()
}

internal fun chromeVersion(userAgent: String): Int? = CHROME_VERSION_REGEX.find(userAgent)?.groupValues?.get(1)?.toIntOrNull()

private fun platform(userAgent: String) = when {
    "Android" in userAgent -> "Android"
    "CrOS" in userAgent -> "Chrome OS"
    "Macintosh" in userAgent -> "macOS"
    "Linux" in userAgent -> "Linux"
    else -> "Windows"
}

/**
 * The device's languages as Chrome and Firefox send them by default: each language with its region, the language
 * alone after its last regional entry, q falling by 0.1.
 */
internal fun acceptLanguage(locales: List<Locale>): String = languageTags(locales).mapIndexed { index, tag ->
    if (index == 0) tag else "$tag;q=0.${(10 - index).coerceAtLeast(1)}"
}.joinToString(",")

private fun languageTags(locales: List<Locale>): List<String> {
    val languages = locales.map { Locale.Builder().setLanguage(it.language).build().toLanguageTag() }
    return buildList {
        locales.forEachIndexed { index, locale ->
            val language = languages[index]
            val tag = if (locale.country.isEmpty()) language else "$language-${locale.country}"
            if (tag !in this) add(tag)
            if (languages.getOrNull(index + 1) != language && language !in this) add(language)
        }
    }
}

// The JVM (Suwayomi, tests) has no LocaleList
private fun deviceLocales(): List<Locale> = try {
    LocaleList.getDefault().let { list -> List(list.size()) { list[it] } }
} catch (_: Throwable) {
    emptyList()
}.ifEmpty { listOf(Locale.getDefault()) }.filter { it.language.isNotEmpty() }

// The OkHttp cache's own directives: core's default max-age, the apps' no-store for covers
private fun String.isAppCacheDirective() = this == DEFAULT_CACHE_CONTROL.toString() || this == "no-store"

// Without OkHttp's public suffix list (Suwayomi, the JVM) the last two labels stand in for it
private fun HttpUrl.registrableDomain(): String? = try {
    topPrivateDomain()
} catch (_: IllegalStateException) {
    host.split('.').takeIf { it.size >= 2 && it.last().any(Char::isLetter) }?.takeLast(2)?.joinToString(".")
}

private fun HttpUrl.origin(): String {
    val host = if (':' in host) "[$host]" else host
    return if (port == HttpUrl.defaultPort(scheme)) "$scheme://$host" else "$scheme://$host:$port"
}

private val CHROME_VERSION_REGEX = Regex("""(?:Chrome|Chromium)/(\d+)""")
private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")
private val JSON_CHARSET = Regex("""(?<=^application/json); ?charset=utf-8$""", RegexOption.IGNORE_CASE)

// Where a page's own headers go in an order
private const val SITE = "*"

private val CHROMIUM_FORKS = listOf("OPR/", "SamsungBrowser/", "YaBrowser/")
private val EDGE_VERSION_REGEX = Regex("""EdgA?/(\d+)""")
private val FIREFOX_VERSION_REGEX = Regex("""Firefox/\d+""")

private val GREASE_CHARS = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
private val GREASE_VERSIONS = listOf("8", "99", "24")
private val BRAND_ORDERS = listOf(
    listOf(0, 1, 2),
    listOf(0, 2, 1),
    listOf(1, 0, 2),
    listOf(1, 2, 0),
    listOf(2, 0, 1),
    listOf(2, 1, 0),
)
