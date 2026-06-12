package eu.kanade.tachiyomi.extension.en.mangareadercc

import eu.kanade.tachiyomi.multisrc.paprika.PaprikaAlt
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MangaReaderIN : PaprikaAlt("MangaReader.in", "https://mangareader.in", "en") {
    override val id = 7388100486112484697

    override val client = super.client.newBuilder()
        .addInterceptor(::chapterListInterceptor)
        .build()

    private fun chapterListInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.fragment != "chapterList") {
            return chain.proceed(request)
        }

        val htmlRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .build()
        val htmlResponse = chain.proceed(htmlRequest)
        val document = htmlResponse.asJsoup()
        val mangaId = document.selectFirst("script:containsData(var mangaID)")
            ?.data()
            ?.substringAfter("var mangaID = '")
            ?.substringBefore("';")
            ?: return htmlResponse.newBuilder()
                .body("".toResponseBody(htmlResponse.body.contentType()))
                .build()

        val mangaTitle = document.selectFirst("div.manga-detail h1")?.text() ?: ""
        val xhrRequest = GET(
            url = "$baseUrl/ajax-list-chapter?mangaID=$mangaId",
            headers = headers.newBuilder().add("X-Manga-Title", mangaTitle).build(),
        )

        return chain.proceed(xhrRequest)
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "#chapterList", headers)

    override fun chapterListSelector() = "li"

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaTitle = response.request.header("X-Manga-Title") ?: ""
        return response.asJsoup()
            .select(chapterListSelector())
            .mapNotNull { chapterFromElement(it, mangaTitle) }
            .distinctBy { it.url }
    }
}
