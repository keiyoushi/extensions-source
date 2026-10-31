package eu.kanade.tachiyomi.extension.en.aquamanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class AquaManga : MadaraNoAjax() {
    override val supportsPostId = false

    override fun archiveSelector() = ".aqua-archive-card"
    override val archiveUrlSelector = ".aqua-archive-card__title a"
    override fun nextPageSelector() = "a.next"
    override val searchQueryParameter = "search"
    override val filterGenresSelector = "div.aqua-genre-pills"
    override val orderByFilterOptions = listOf(
        intl["order_by_filter_latest"] to "latest",
        intl["order_by_filter_rating"] to "views",
        intl["order_by_filter_new"] to "new-manga",
        intl["order_by_filter_az"] to "alphabet",
    )

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/$mangaSubString/").asJsoup()
        return document.selectFirst(filterGenresSelector)?.select("a.aqua-filter-pill")?.mapNotNull { element ->
            val href = element.attr("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val genre = href.substringAfter("genre=", "")
            val name = element.text()
            name to genre
        }?.distinctBy { it.second }.orEmpty().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>()
        return FilterList(
            buildList {
                // Status filter returns randomized result, don't add it unless they fixed it
                add(OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions, "views"))
                if (genres?.isNotEmpty() == true) add(SingleGenreSelectFilter(intl["genre_filter_title"], genres, ""))
            },
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(mangaSubString)
            addPathSegment("")
            if (page > 1) addPathSegments("page/$page/")
            filters.firstInstanceOrNull<OrderByFilter>()?.selected?.let {
                addQueryParameter(orderQueryParameter, it)
            }
            filters.firstInstanceOrNull<SingleGenreSelectFilter>()?.selected?.let {
                addQueryParameter("genre", it)
            }
            if (query.isNotBlank()) addQueryParameter(searchQueryParameter, query)
        }.build()
        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

    override val mangaDetailsSelectorTitle = ".aqua-series-info__title"
    override val mangaDetailsSelectorThumbnail = ".aqua-series-cover__img"
    override val mangaDetailsSelectorDescription = ".aqua-series-synopsis"
    override val mangaDetailsSelectorStatus = ".aqua-series-meta__status"
    override val mangaDetailsSelectorGenre = ".aqua-series-genre-pill"
    override val mangaDetailsSelectorAuthor = ".aqua-series-info__creator-value a"
    override val mangaDetailsSelectorArtist = ".aqua-series-info__creator-value a"

    override fun chapterListSelector() = ".aqua-ch-item"

    override val chapterUrlSelector = "a"
    override val chapterNameSelector = ".aqua-ch-item__name"
    override val chapterDateSelector = ".aqua-ch-item__time"
}
