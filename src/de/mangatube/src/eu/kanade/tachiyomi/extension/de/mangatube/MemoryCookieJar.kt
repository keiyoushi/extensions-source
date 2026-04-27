package eu.kanade.tachiyomi.extension.de.mangatube

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val current = store.getOrPut(host) { mutableListOf() }

        for (newCookie in cookies) {
            current.removeAll { old ->
                old.name == newCookie.name &&
                    old.domain == newCookie.domain &&
                    old.path == newCookie.path
            }
            if (!newCookie.expiresAt.let { it < System.currentTimeMillis() }) {
                current += newCookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val hostCookies = store[url.host].orEmpty()

        return hostCookies.filter { cookie ->
            cookie.expiresAt >= now && cookie.matches(url)
        }
    }
}
