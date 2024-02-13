package eu.kanade.tachiyomi.extension.tr.hentaizm

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import rx.Observable

class HentaiZM : Madara(
    "HentaiZM",
    "https://manga.hentaizm.fun",
    "tr",
) {
    override val client by lazy {
        super.client.newBuilder().addInterceptor(::loginInterceptor).build()
    }

    private fun loginInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        if (baseUrl !in url) {
            return chain.proceed(request)
        }

        val cookies = client.cookieJar.loadForRequest(request.url)

        // Only log-in when necessary.
        if (cookies.any { it.name.startsWith("wordpress_logged_in_") }) {
            return chain.proceed(request)
        }

        // A login is required in order to load thumbnails and pages.
        val body = FormBody.Builder()
            .add("log", "demo") // Default user/password, provided in
            .add("pwd", "demo") // the source itself.
            .add("redirect_to", "$baseUrl/wp-admin/")
            .add("rememberme", "forever")
            .build()

        val postUrl = "$baseUrl/wp-login.php"
        val headers = headersBuilder()
            .set("Origin", baseUrl)
            .set("Referer", postUrl)
            .build()

        super.client.newCall(POST(postUrl, headers, body)).execute().close()

        return chain.proceed(request)
    }

    // ============================== Popular ===============================
    // Yep, asObservable instead of asObservableSuccess, because this source
    // returns HTTP 404 after the first page even in the browser, while working
    // perfectly.
    // TODO: Replace with getPopularManga(page) when extensions-lib v1.5 gets released.
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservable()
            .map(::popularMangaParse)
    }

    // =============================== Latest ===============================
    // Same situation as above.
    // TODO: Replace with getLatestUpdates(page) when extensions-lib v1.5 gets released.
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservable()
            .map(::latestUpdatesParse)
    }
}
