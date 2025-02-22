package eu.kanade.tachiyomi.extension.en.zahard

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class Zahard : MangaThemesia(
    "Zahard",
    "https://zahard.xyz",
    "en",
    mangaUrlDirectory = "/library",
) {
    override val versionId = 2

    override val supportsLatest = false

    override val pageSelector = "div#chapter_imgs img"

    override fun searchMangaNextPageSelector() = "a[rel=next]"

    override fun chapterListSelector() = "#chapterlist > ul > a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList()
}
