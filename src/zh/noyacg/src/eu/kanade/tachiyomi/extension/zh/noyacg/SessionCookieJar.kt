package eu.kanade.tachiyomi.extension.zh.noyacg

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SessionCookieJar(private val homeUrl: HttpUrl, private val cookieJar: CookieJar) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = cookieJar.saveFromResponse(url, cookies)

    override fun loadForRequest(url: HttpUrl) = cookieJar.loadForRequest(if (url.host.startsWith("api")) homeUrl else url)
}
