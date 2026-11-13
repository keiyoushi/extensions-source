package eu.kanade.tachiyomi.extension.vi.sangchanhteam

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneId
import java.time.ZoneOffset

@Source
abstract class SangChanhTeam : InitManga() {

    override val mangaUrlDirectory = "truyen"

    override val popularUrlSlug = "bang-xep-hang-truyen"

    override val latestUrlSlug = "moi-cap-nhat"

    override val authorLabel = "Tác giả"

    override val artistLabel = "Họa sĩ minh họa"

    override val chapterPrefix = "Chương"

    override val altTitleLabel = "Tên khác"

    override val siteZone: ZoneId = ZoneOffset.ofHours(7)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return super.getSearchMangaList(page, query, filters)

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("bo-loc-nang-cao")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")

            addQueryParameter("type", filters.firstInstanceOrNull<TypeFilter>()?.toUriPart().orEmpty())
            addQueryParameter("status", filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty())
            addQueryParameter("age_rating", filters.firstInstanceOrNull<AgeRatingFilter>()?.toUriPart().orEmpty())
            filters.firstInstanceOrNull<GenreFilter>()
                ?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("genre[]", it.slug) }
            addQueryParameter("team", "")
            addQueryParameter("rating_min", "0")
            addQueryParameter("rating_max", "6")
            addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "updated")
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.manga-item-details").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h2 a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.uk-pagination li:not(.uk-disabled) a:has([uk-pagination-next])") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/bo-loc-nang-cao/", Fetch.DOCUMENT).asJsoup()
        .select("input.genre-checkbox[value]")
        .mapNotNull { element ->
            val name = element.attr("data-genre-name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val slug = element.attr("value").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GenreOption(name, slug)
        }
        .distinctBy { it.slug }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())
}
