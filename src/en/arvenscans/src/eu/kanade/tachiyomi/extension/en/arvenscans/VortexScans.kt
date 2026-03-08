package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
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
            addQueryParameter("perPage", Iken.PER_PAGE.toString())
            addQueryParameter("tag", "new")
            addQueryParameter("isNovel", "false")
        }.build()

        return GET(url, headers)
    }

    override val popularSubString = "posts"

    override val usePopularMangaApi = true

    override fun popularMangaUrl(page: Int) = super.popularMangaUrl(page)
        .addQueryParameter("tag", "hot")
        .addQueryParameter("isNovel", "false")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservable()
        .map(::chapterListParse)
}
