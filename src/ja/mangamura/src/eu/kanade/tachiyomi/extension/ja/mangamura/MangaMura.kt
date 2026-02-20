package eu.kanade.tachiyomi.extension.ja.mangamura

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class MangaMura :
    MangaReader(
        "Manga Mura",
        "https://mangamura.net",
        "ja",
    ) {
    override val chapterIdSelect = "ja-chaps"

    override fun getAjaxUrl(id: String): String = "$baseUrl/json/chapter?mode=vertical&id=$id"

    override val searchPathSegment = ""
    override val searchKeyword = "q"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)

        // avoid 302
        val newUrl = request.url.newBuilder()
            .addPathSegment("")
            .build()

        return request.newBuilder()
            .url(newUrl)
            .build()
    }

    override fun getFilterList(): FilterList = FilterList(
        Note,
        TypeFilter(),
        StatusFilter(),
        LanguageFilter(),
        SortFilter(),
    )
}
