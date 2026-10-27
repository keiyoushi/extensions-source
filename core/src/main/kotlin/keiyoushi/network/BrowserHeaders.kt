package keiyoushi.network

import android.os.LocaleList
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

/**
 * Adds the headers the browser in a request's `User-Agent` sends for its [RequestKind]: client hints,
 * `Accept-Language`, fetch metadata, `Accept` and `Priority`, and keeps KeiSource's default `Origin` only where
 * browsers send one. Covers the apps load count as images. A request without a kind goes out as built.
 *
 * Headers a request already has are kept. The page a request comes from is its `Referer`.
 */
internal class BrowserHeadersInterceptor(
    private val siteUrl: () -> String,
    private val browserImageHeaders: () -> Boolean = { true },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isRaw) return chain.proceed(request)
        // The apps' cover requests carry no kind, only the no-store that keeps covers out of the OkHttp cache
        val kind = request.kind
            ?: RequestKind.IMAGE.takeIf { request.method == "GET" && request.header("Cache-Control") == "no-store" && browserImageHeaders() }
            ?: return chain.proceed(request)
        val site = siteUrl().toHttpUrl()
        val initiator = request.header("Referer")?.toHttpUrlOrNull() ?: site
        val userAgent = request.header("User-Agent").orEmpty()
        val browser = Browser.of(userAgent)
        // KeiSource's default Origin is ours to keep or drop; one the source set or removed itself stays
        val ownOrigin = request.header("Origin").let { it == null || it == siteUrl() || it == site.origin() }
        val builder = request.newBuilder()
            .tag(RequestKind::class.java, kind)
            .tag(Initiator::class.java, Initiator(initiator, request.header("Sec-Fetch-Site") == null, ownOrigin))
        fun fill(name: String, value: String?) {
            if (value != null && request.header(name) == null) builder.header(name, value)
        }

        // A client that doesn't present itself as a browser (an API's own user agent) gets no browser headers
        val presentsAsBrowser = userAgent.startsWith("Mozilla/")
        // Browsers send fetch metadata and client hints to secure origins only
        val secure = request.url.isHttps || request.url.host in LOCAL_HOSTS
        if (presentsAsBrowser) {
            if (secure) browser?.clientHints(userAgent)?.forEach { (name, value) -> fill(name, value) }
            fill("Accept-Language", acceptLanguage(deviceLocales()).ifEmpty { null })
            fill("Accept", browser?.accept(kind))
            if (secure) {
                fill("Sec-Fetch-Site", relation(initiator, request.url))
                fill(
                    "Sec-Fetch-Mode",
                    when (kind) {
                        RequestKind.DOCUMENT -> "navigate"
                        RequestKind.XHR -> "cors"
                        else -> "no-cors"
                    },
                )
                if (kind == RequestKind.DOCUMENT) fill("Sec-Fetch-User", "?1")
                fill(
                    "Sec-Fetch-Dest",
                    when (kind) {
                        RequestKind.DOCUMENT -> "document"
                        RequestKind.XHR -> "empty"
                        RequestKind.IMAGE -> "image"
                        RequestKind.SCRIPT -> "script"
                        RequestKind.STYLE -> "style"
                        RequestKind.AUDIO -> "audio"
                        RequestKind.VIDEO -> "video"
                    },
                )
            }
            if (kind == RequestKind.DOCUMENT) fill("Upgrade-Insecure-Requests", "1")
            // Media loads ask for the bytes as stored, from the start
            if (kind == RequestKind.AUDIO || kind == RequestKind.VIDEO) {
                fill("Accept-Encoding", "identity")
                fill("Range", "bytes=0-")
            }
            fill("Priority", browser?.priority(kind))
        }
        if (ownOrigin) {
            if (sendsOrigin(request.method, kind, initiator, request.url)) {
                builder.header("Origin", initiator.origin())
            } else {
                builder.removeHeader("Origin")
            }
        }

        return chain.proceed(builder.build())
    }
}

/** The page a request comes from, and whether its `Sec-Fetch-Site` and `Origin` are ours to update on a redirect. */
internal class Initiator(val url: HttpUrl, val ownSite: Boolean, val ownOrigin: Boolean) {
    // The least related site a redirect chain has passed through, as browsers report it
    var site: String? = null
}

private fun relation(initiator: HttpUrl, url: HttpUrl): String = when {
    initiator.origin() == url.origin() -> "same-origin"
    (initiator.registrableDomain() ?: initiator.host) == (url.registrableDomain() ?: url.host) -> "same-site"
    else -> "cross-site"
}

private val SITES = listOf("same-origin", "same-site", "cross-site")

// Browsers send Origin on methods other than GET and HEAD and on cross-origin XHRs
private fun sendsOrigin(method: String, kind: RequestKind, initiator: HttpUrl, url: HttpUrl) = (method != "GET" && method != "HEAD") || (kind == RequestKind.XHR && initiator.origin() != url.origin())

/**
 * Puts the headers in the order the browser in the `User-Agent` sends them. On the wire it also drops what that
 * browser doesn't send (`Priority` over HTTP/1.1 in Chrome, the OkHttp cache's own `Cache-Control` directives) and
 * adds Chrome's `Cache-Control` on a form submit. Runs per hop on the network level, so it sees `Cookie`, the body
 * headers OkHttp adds and each redirect.
 */
internal object BrowserHeaderOrderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isRaw) return chain.proceed(request)
        val kind = request.kind ?: return chain.proceed(request)
        val browser = Browser.of(request.header("User-Agent").orEmpty()) ?: return chain.proceed(request)
        val http2 = chain.connection()?.protocol() == Protocol.HTTP_2
        val navigation = kind == RequestKind.DOCUMENT
        val order = when (kind) {
            RequestKind.DOCUMENT -> browser.documentOrder
            RequestKind.AUDIO, RequestKind.VIDEO -> browser.mediaOrder
            else -> browser.subresourceOrder
        }
        val hop = request.redirectHop()
        val rank = { name: String -> order.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 } ?: order.indexOf(SITE) }
        val headers = hop.headers
            .filterNot { (name, value) -> name.equals("Cache-Control", ignoreCase = true) && value.isAppCacheDirective() }
            .filter { (name) -> http2 || browser.priorityOnHttp1 || !name.equals("Priority", ignoreCase = true) }
            .map { (name, value) ->
                // Pages send application/json without a charset (axios, fetch with the type set)
                val json = request.body != null && name.equals("Content-Type", ignoreCase = true)
                name to if (json) value.replace(JSON_CHARSET, "") else value
            }
            .plus(if (http2 && browser.sendsTeTrailers) listOf("te" to "trailers") else emptyList())
            .let { list ->
                val formSubmit = browser.formSubmitCacheControl?.takeIf { navigation && request.method == "POST" }
                val hasCacheControl = list.any { (name) -> name.equals("Cache-Control", ignoreCase = true) }
                if (formSubmit != null && !hasCacheControl) list + ("Cache-Control" to formSubmit) else list
            }
            // Stable: a site's own headers keep the order they were added in
            .sortedBy { (name) -> rank(name) }
        val wire = Headers.Builder().apply { headers.forEach { (name, value) -> add(name, value) } }.build()
        // The cache keeps the request as it was before the reordering
        return chain.proceed(request.newBuilder().headers(wire).build()).newBuilder().request(request).build()
    }

    // A redirect hop's Sec-Fetch-Site and Origin as the browser would send them there
    private fun Request.redirectHop(): Request {
        val initiator = tag(Initiator::class.java) ?: return this
        val builder = newBuilder()
        if (initiator.ownSite && header("Sec-Fetch-Site") != null) {
            val site = listOfNotNull(initiator.site, relation(initiator.url, url)).maxBy(SITES::indexOf)
            initiator.site = site
            builder.header("Sec-Fetch-Site", site)
        }
        if (initiator.ownOrigin && !sendsOrigin(method, kind!!, initiator.url, url)) builder.removeHeader("Origin")
        return builder.build()
    }
}

/** A browser's values per request kind and its header order. */
internal sealed class Browser {
    abstract fun accept(kind: RequestKind): String
    abstract fun priority(kind: RequestKind): String
    open fun clientHints(userAgent: String): Map<String, String> = emptyMap()

    // The orders measured over HTTP/2, [SITE] marking where a page's own headers go. Over HTTP/1.1 browsers also
    // move Connection and Cookie and change name case; matching that too would take a lot more rules for little gain
    abstract val documentOrder: List<String>
    abstract val subresourceOrder: List<String>
    open val mediaOrder get() = subresourceOrder
    open val priorityOnHttp1 = false
    open val sendsTeTrailers = false
    open val formSubmitCacheControl: String? = null

    // Chrome 155
    data object Chrome : Browser() {
        override fun accept(kind: RequestKind) = when (kind) {
            RequestKind.DOCUMENT ->
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/jxl,image/avif,image/webp,image/apng,*/*;q=0.8," +
                    "application/signed-exchange;v=b3;q=0.7"
            RequestKind.IMAGE -> "image/jxl,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
            RequestKind.STYLE -> "text/css,*/*;q=0.1"
            RequestKind.XHR, RequestKind.SCRIPT, RequestKind.AUDIO, RequestKind.VIDEO -> "*/*"
        }

        // Scripts and stylesheets as loaded from the page's head
        override fun priority(kind: RequestKind) = when (kind) {
            RequestKind.DOCUMENT -> "u=0, i"
            RequestKind.IMAGE, RequestKind.AUDIO, RequestKind.VIDEO -> "i"
            RequestKind.XHR -> "u=1, i"
            RequestKind.SCRIPT -> "u=1"
            RequestKind.STYLE -> "u=0"
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
            "Accept-Encoding", "Accept-Language", "Range", "Priority", "Cookie",
        )

        override val formSubmitCacheControl = "max-age=0"
    }

    // Firefox 158
    data object Firefox : Browser() {
        override fun accept(kind: RequestKind) = when (kind) {
            RequestKind.DOCUMENT -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            RequestKind.IMAGE -> "image/avif,image/jxl,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5"
            RequestKind.STYLE -> "text/css,*/*;q=0.1"
            RequestKind.AUDIO -> "audio/webm,audio/ogg,audio/wav,audio/*;q=0.9,application/ogg;q=0.7,video/*;q=0.6,*/*;q=0.5"
            RequestKind.VIDEO -> "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5"
            RequestKind.XHR, RequestKind.SCRIPT -> "*/*"
        }

        // XHR as fetch() sends it (Firefox's XMLHttpRequest sends none); scripts and stylesheets from the page's head
        override fun priority(kind: RequestKind) = when (kind) {
            RequestKind.DOCUMENT -> "u=0, i"
            RequestKind.IMAGE -> "u=5, i"
            RequestKind.XHR, RequestKind.AUDIO, RequestKind.VIDEO -> "u=4"
            RequestKind.SCRIPT, RequestKind.STYLE -> "u=2"
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
        override val mediaOrder = listOf(
            "Host", "User-Agent", "Accept", "Accept-Language", "Range", "Connection", "Referer", SITE, "Sec-Fetch-Dest",
            "Sec-Fetch-Mode", "Sec-Fetch-Site", "Accept-Encoding", "Priority", "TE", "Cookie",
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
private fun String.isAppCacheDirective() = split(",").map { it.trim().lowercase() }.all {
    it.startsWith("max-age=") || it.startsWith("max-stale") || it == "no-store"
}

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
