package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class VortexScans :
    Iken(
        "Vortex Scans",
        "en",
        "https://vortexscans.org",
        "https://api.vortexscans.org",
    ) {
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("tag", "new")
            addQueryParameter("isNovel", "false")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/api/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("tag", "hot")
            addQueryParameter("isNovel", "false")
        }.build()

        return GET(url, headers)
    }
    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    val perPage = 18

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservable()
        .map(::chapterListParse)
}
