package eu.kanade.tachiyomi.extension.vi.meosss

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.ZoneOffset

@Source
abstract class MeoSSS : InitManga() {

    override val mangaUrlDirectory = "truyen"

    override val popularUrlSlug = "bang-xep-hang-truyen"

    override val latestUrlSlug = "moi-cap-nhat"

    override val authorLabel = "Tác giả"

    override val artistLabel = "Họa sĩ minh họa"

    override val chapterPrefix = "Chương"

    override val altTitleLabel = "Tên khác"

    override val siteZone: ZoneId = ZoneOffset.ofHours(7)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addImageDecryptInterceptor().rateLimit(3)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return super.getSearchMangaList(page, query, filters)

        val path = "/bo-loc-nang-cao/" + if (page > 1) "page/$page/" else ""
        val url = "$baseUrl$path".toHttpUrl().newBuilder().apply {
            filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("genre[]", it.value) }
            filters.firstInstanceOrNull<StatusFilter>()?.let {
                val statusValue = statusValues[it.state]
                if (statusValue.isNotEmpty()) addQueryParameter("status", statusValue)
            }
            filters.firstInstanceOrNull<AgeRatingFilter>()?.let {
                val ageValue = ageValues[it.state]
                if (ageValue.isNotEmpty()) addQueryParameter("age_rating", ageValue)
            }
            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("sort", sortValues[it.state])
            }
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
        .select("input[name=genre[]]")
        .map { input ->
            GenreOption(
                name = input.attr("data-genre-name").ifEmpty { input.attr("value") },
                value = input.attr("value"),
            )
        }
        .sortedBy { it.name }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>().orEmpty())

    private val statusValues = arrayOf("", "ongoing", "season_end", "completed", "source_hiatus", "caught_up", "dropped")
    private val ageValues = arrayOf("", "all", "13+", "16+", "18+")
    private val sortValues = arrayOf("updated", "new", "old", "views", "views_day", "views_week", "views_month", "rating", "power", "follow")
}
