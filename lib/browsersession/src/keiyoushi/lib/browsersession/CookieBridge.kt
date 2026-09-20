package keiyoushi.lib.browsersession

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Bridge between Android's [CookieManager] and OkHttp.
 *
 * Provides safe cookie retrieval, synchronization to OkHttp requests, explicit flush barriers,
 * and multi-domain scoped cookie purges to eradicate host-specific and wildcard cookies.
 */
object CookieBridge {

    /**
     * Resolves all scoped domain variations for [url], from its specific host up to
     * its top private domain, including leading-dot wildcard variations.
     *
     * For example, `https://manga.sub.example.com/` returns:
     * - `manga.sub.example.com`
     * - `.manga.sub.example.com`
     * - `sub.example.com`
     * - `.sub.example.com`
     * - `example.com`
     * - `.example.com`
     */
    fun getScopedDomains(url: HttpUrl): List<String> {
        val host = url.host
        val topDomain = runCatching { url.topPrivateDomain() }.getOrNull()
            ?: computeFallbackTopPrivateDomain(host)
        val domains = linkedSetOf<String>()

        domains.add(host)
        domains.add(".$host")

        if (topDomain != null && topDomain != host) {
            var current = host
            while (current != topDomain && current.contains('.')) {
                val next = current.substringAfter('.', "")
                if (next.isNotEmpty() && (next == topDomain || next.endsWith(".$topDomain"))) {
                    domains.add(next)
                    domains.add(".$next")
                    current = next
                } else {
                    break
                }
            }
            domains.add(topDomain)
            domains.add(".$topDomain")
        }

        return domains.toList()
    }

    internal fun computeFallbackTopPrivateDomain(host: String): String? {
        if (host.isEmpty() || host.all { it.isDigit() || it == '.' } || !host.contains('.')) {
            return null
        }

        val lowerHost = host.lowercase()
        val isCompound = COMPOUND_TLD_SUFFIXES.any { lowerHost.endsWith(it) }

        val parts = lowerHost.split('.')
        val neededParts = if (isCompound) 3 else 2

        return if (parts.size >= neededParts) {
            parts.takeLast(neededParts).joinToString(".")
        } else {
            null
        }
    }

    private val COMPOUND_TLD_SUFFIXES = listOf(
        ".co.uk", ".org.uk", ".gov.uk", ".ac.uk",
        ".com.au", ".net.au", ".org.au",
        ".co.jp", ".ne.jp",
        ".co.kr",
        ".com.br", ".org.br",
        ".com.mx",
        ".co.nz",
        ".com.tw",
        ".com.tr",
        ".com.sg",
        ".com.ar",
        ".com.co",
        ".co.id",
        ".com.ph",
        ".com.my",
        ".com.es",
        ".com.pt",
        ".co.th",
        ".com.vn",
    )

    /**
     * Parses a raw `Cookie` header string (as returned by [CookieManager.getCookie]) into
     * a list of OkHttp [Cookie] instances.
     *
     * Unlike OkHttp's default Set-Cookie parser, this ensures that parsed cookies default to
     * path="/" and are properly scoped so they match all subsequent requests on the domain.
     */
    fun parseCookieHeader(url: HttpUrl, rawHeader: String?): List<Cookie> {
        if (rawHeader.isNullOrBlank()) return emptyList()

        val isIp = url.host.all { it.isDigit() || it == '.' } || url.host == "localhost"

        return rawHeader.split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx == -1) return@mapNotNull null

                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                if (name.isEmpty()) return@mapNotNull null

                runCatching {
                    val builder = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .path("/")

                    if (isIp) {
                        builder.hostOnlyDomain(url.host)
                    } else {
                        builder.domain(url.host)
                    }
                    builder.build()
                }.getOrNull() ?: runCatching {
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .hostOnlyDomain(url.host)
                        .path("/")
                        .build()
                }.getOrNull()
            }
    }

    /**
     * Safely reads and parses all cookies for [url] from [CookieManager].
     */
    fun getCookies(url: HttpUrl): List<Cookie> {
        val rawHeader = getCookieHeader(url) ?: return emptyList()
        return parseCookieHeader(url, rawHeader)
    }

    /**
     * Safely reads and parses all cookies for [url] string from [CookieManager].
     */
    fun getCookies(url: String): List<Cookie> = runCatching { url.toHttpUrl() }.getOrNull()?.let { getCookies(it) } ?: emptyList()

    /**
     * Retrieves the raw `Cookie` header string for [url] from [CookieManager],
     * or null if no cookies are set.
     */
    fun getCookieHeader(url: HttpUrl): String? {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return null
        val rawHeader = runCatching { cookieManager.getCookie(url.toString()) }.getOrNull()
        return rawHeader?.takeIf { it.isNotBlank() }
    }

    /**
     * Retrieves the raw `Cookie` header string for [url] string from [CookieManager],
     * or null if no cookies are set.
     */
    fun getCookieHeader(url: String): String? = runCatching { url.toHttpUrl() }.getOrNull()?.let { getCookieHeader(it) }

    /**
     * Retrieves the value of the cookie named [name] for [url], or null if absent.
     */
    fun getCookieValue(url: HttpUrl, name: String): String? = getCookies(url).firstOrNull { it.name == name }?.value

    /**
     * Injects cookies from [CookieManager] matching [url] into the given [builder].
     */
    fun applyCookies(builder: Request.Builder, url: HttpUrl): Request.Builder {
        val header = getCookieHeader(url)
        if (!header.isNullOrBlank()) {
            builder.header("Cookie", header)
        }
        return builder
    }

    /**
     * Sets a cookie in [CookieManager] and commits changes via [CookieManager.flush].
     */
    fun setCookie(url: HttpUrl, cookie: Cookie) {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        runCatching {
            cookieManager.setCookie(url.toString(), cookie.toString())
            cookieManager.flush()
        }
    }

    /**
     * Sets a cookie with specified attributes in [CookieManager] and commits changes via [CookieManager.flush].
     */
    fun setCookie(
        url: HttpUrl,
        name: String,
        value: String,
        domain: String? = null,
        path: String = "/",
    ) {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val cookieStr = buildString {
            append(name).append('=').append(value)
            if (!domain.isNullOrBlank()) {
                append("; Domain=").append(domain)
            }
            append("; Path=").append(path)
        }
        runCatching {
            cookieManager.setCookie(url.toString(), cookieStr)
            cookieManager.flush()
        }
    }

    /**
     * Sets multiple cookies in [CookieManager] and performs a single [CookieManager.flush] barrier.
     */
    fun setCookies(url: HttpUrl, cookies: Iterable<Cookie>) {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        runCatching {
            for (cookie in cookies) {
                cookieManager.setCookie(url.toString(), cookie.toString())
            }
            cookieManager.flush()
        }
    }

    /**
     * Purges cookies matching [cookieNames] across all scoped domains of [url]
     * (host, `.$host`, `topPrivateDomain`, `.$topPrivateDomain`, and any intermediate subdomains).
     *
     * If [cookieNames] is empty, all cookies currently associated with [url] are purged.
     */
    fun clearCookies(url: HttpUrl, cookieNames: Collection<String>) {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val domains = getScopedDomains(url)

        val namesToClear = if (cookieNames.isEmpty()) {
            getCookies(url).map { it.name }.distinct()
        } else {
            cookieNames.distinct()
        }

        if (namesToClear.isEmpty()) return

        val schemes = listOf("https", "http")

        val paths = if (url.encodedPath != "/") listOf("/", url.encodedPath) else listOf("/")

        for (name in namesToClear) {
            for (path in paths) {
                // Host-only cookie expiration on exact URL
                val hostClearValue = "$name=; Path=$path; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
                runCatching { cookieManager.setCookie(url.toString(), hostClearValue) }

                // Domain and wildcard expiration across all scoped domains and schemes
                for (domain in domains) {
                    val domainClean = domain.removePrefix(".")
                    val clearValue = "$name=; Domain=$domain; Path=$path; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
                    for (scheme in schemes) {
                        val endpointUrl = "$scheme://$domainClean$path"
                        runCatching { cookieManager.setCookie(endpointUrl, clearValue) }
                    }
                }
            }
        }

        // Explicit flush barrier
        runCatching { cookieManager.flush() }
    }

    /**
     * Purges cookies matching [cookieNames] across all scoped domains of [url].
     */
    fun clearCookies(url: HttpUrl, vararg cookieNames: String) {
        clearCookies(url, cookieNames.toList())
    }

    /**
     * Purges cookies matching [cookieNames] across all scoped domains of [url] string.
     */
    fun clearCookies(url: String, vararg cookieNames: String) {
        runCatching { url.toHttpUrl() }.getOrNull()?.let { clearCookies(it, cookieNames.toList()) }
    }

    /**
     * Purges cookies matching [cookieNames] across all scoped domains of [url] string.
     */
    fun clearCookies(url: String, cookieNames: Collection<String>) {
        runCatching { url.toHttpUrl() }.getOrNull()?.let { clearCookies(it, cookieNames) }
    }
}
