package eu.kanade.tachiyomi.extension.zh.bilimanga

import eu.kanade.tachiyomi.extension.zh.bilimanga.BiliManga.Companion.MANGA_ID_REGEX
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okio.GzipSource
import okio.buffer

class MangaInterceptor : Interceptor {

    companion object {
        val PREV_URL_REGEX = Regex("url_previous:'(.*?)'")
        val NEXT_URL_REGEX = Regex("url_next:'(.*?)'")
        val CHAPTER_ID_REGEX = Regex("/read/(\\d+)/(\\d+)\\.html")
    }

    private fun regexOf(str: String?) = when (str) {
        "prev" -> PREV_URL_REGEX
        "next" -> NEXT_URL_REGEX
        else -> null
    }

    private fun predictUrlByContext(url: HttpUrl) = when (url.fragment) {
        "prev" -> {
            val groups = CHAPTER_ID_REGEX.find(url.toString())?.groups
            "/read/${groups?.get(1)?.value}/${groups?.get(2)?.value?.toInt()?.plus(1)}.html"
        }
        "next" -> {
            val groups = CHAPTER_ID_REGEX.find(url.toString())?.groups
            "/read/${groups?.get(1)?.value}/${groups?.get(2)?.value?.toInt()?.minus(1)}.html"
        }
        else -> "/read/0/0.html"
    } + "?predict"

    override fun intercept(chain: Interceptor.Chain): Response {
        val origin = chain.request()
        regexOf(origin.url.fragment)?.let {
            val response = chain.proceed(origin)
            val html = GzipSource(response.body.source()).buffer().readUtf8()
            val url = it.find(html)?.groups?.get(1)?.value?.plus("?match")
            return response.newBuilder().code(302)
                .header("Location", url ?: predictUrlByContext(origin.url)).build()
        }
        MANGA_ID_REGEX.find(origin.url.toString())?.groups?.get(1)?.value?.let {
            val new = origin.newBuilder()
                .addHeader("Cookie", "jieqiVisitId=cartoon_cartoonviews%3D$it")
                .build()
            return chain.proceed(new)
        }
        return chain.proceed(origin)
    }
}
