package eu.kanade.tachiyomi.extension.en.novelcrow

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement

@Source
abstract class NovelCrow : MadaraNoAjax() {
    override val mangaSubString = "comic"
    override val genreDirectory = "comic-genre"
    override val chapterMode = ChapterMode.MangaAjax
    override val mangaDetailsSelectorAuthor = "div.artist-content > a"

    // /comic/ archive 404s, the search page lists everything instead
    override fun archiveUrlBuilder(page: Int, order: String, path: String, query: String) = if (path == "/$mangaSubString/") {
        searchUrlBuilder(page, query).apply { if (order.isNotBlank()) addQueryParameter(orderQueryParameter, order) }
    } else {
        super.archiveUrlBuilder(page, order, path, query)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isBlank()) {
        super.getSearchMangaList(page, query, filters)
    } else {
        archivePage(page, "", query = query)
    }

    override suspend fun fetchFilterData(): JsonElement = client.get(searchUrlBuilder(1, "").build()).asJsoup()
        .select("input[name='genre[]']")
        .map { GenreRoute(it.parent()!!.text(), it.attr("value"), "/$genreDirectory/${it.attr("value")}/") }
        .toJsonElement()
}
