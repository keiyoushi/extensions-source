package eu.kanade.tachiyomi.extension.zh.bilimanga

import eu.kanade.tachiyomi.extension.zh.bilimanga.BiliManga.Companion.MANGA_ID_REGEX
import okhttp3.Interceptor
import okhttp3.Response

class MangaDetailInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val origin = chain.request()
        val id = MANGA_ID_REGEX.find(origin.url.toString())?.groups?.get(1)?.value
        if (id != null) {
            val new = origin.newBuilder()
                .addHeader("Cookie", "jieqiVisitId=cartoon_cartoonviews%3D$id")
                .build()
            return chain.proceed(new)
        }
        return chain.proceed(origin)
    }
}
