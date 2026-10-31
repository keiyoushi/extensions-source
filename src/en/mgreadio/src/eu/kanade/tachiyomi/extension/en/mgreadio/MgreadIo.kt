package eu.kanade.tachiyomi.extension.en.mgreadio

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneId
import java.time.ZoneOffset

@Source
abstract class MgreadIo : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "manga-ranking"

    override val latestUrlSlug = "recently-updated"

    override val chapterPrefix = "Chapter"

    override val altTitleLabel = "Alternative title"

    override val siteZone: ZoneId = ZoneOffset.ofHours(7)

    override suspend fun getPopularManga(page: Int): MangasPage = super.getPopularManga(page).withoutAnime()

    override suspend fun getLatestUpdates(page: Int): MangasPage = super.getLatestUpdates(page).withoutAnime()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return super.getSearchMangaList(page, query, filters).withoutAnime()

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("advanced-filter")
            .apply { if (page > 1) addPathSegment("page").addPathSegment(page.toString()) }
            .addPathSegment("")
            .apply { addFilters(filters) }
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.manga-item-details").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h2 a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.uk-pagination li:not(.uk-disabled) a:has([uk-pagination-next])") != null
        return MangasPage(mangas, hasNextPage).withoutAnime()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = super.fetchRelatedMangaList(manga).filterNot { it.isAnime() }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/advanced-filter/").asJsoup()
        .select("input[name='genre[]'][data-genre-name][value]:not([value=anime])")
        .map { GenreOption(it.attr("data-genre-name"), it.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf(
            Filter.Header("Filters are only applied when the search text is empty."),
            TypeFilter(),
            StatusFilter(),
            AgeRatingFilter(),
            RatingMinFilter(),
            RatingMaxFilter(),
            SortFilter(),
        )
        data?.parseAs<List<GenreOption>>()?.takeIf { it.isNotEmpty() }?.let {
            filters += GenreFilter(it)
        }
        return FilterList(filters)
    }

    // Anime episode posts listed among the series
    private fun SManga.isAnime() = url.removePrefix("/manga/").startsWith("anime-")

    private fun MangasPage.withoutAnime() = MangasPage(mangas.filterNot { it.isAnime() }, hasNextPage)
}
