package eu.kanade.tachiyomi.extension.vi.sangchanhteam

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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

    private val thumbnailFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = request.url.fragment
            ?.toHttpUrlOrNull()
            ?: return@Interceptor response

        if (response.code != 401 && response.code != 404) {
            return@Interceptor response
        }

        response.close()

        // WordPress keeps uploads over 2560px only as "-scaled"
        val segments = request.url.pathSegments
        val scaledUrl = request.url.newBuilder()
            .setPathSegment(segments.lastIndex, segments.last().replace(fileExtensionRegex, "-scaled$1"))
            .fragment(null)
            .build()
        val scaledResponse = chain.proceed(GET(scaledUrl, request.headers))
        if (scaledResponse.isSuccessful) return@Interceptor scaledResponse

        scaledResponse.close()
        chain.proceed(GET(fallbackUrl, request.headers))
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addImageDecryptInterceptor()
        .addInterceptor(thumbnailFallbackInterceptor)
        .rateLimit(3)

    override suspend fun getPopularManga(page: Int): MangasPage = super.getPopularManga(page).withFullThumbnails()

    override suspend fun getLatestUpdates(page: Int): MangasPage = super.getLatestUpdates(page).withFullThumbnails()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return super.getSearchMangaList(page, query, filters).withFullThumbnails()

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
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.fullImageUrl()
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

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = super.fetchRelatedMangaList(manga)
        .onEach { it.thumbnail_url = it.thumbnail_url?.fullImageUrl() }

    private fun MangasPage.withFullThumbnails() = apply {
        mangas.forEach { it.thumbnail_url = it.thumbnail_url?.fullImageUrl() }
    }

    private fun String.fullImageUrl(): String {
        val originalUrl = this
        val fullSizeUrl = replace(thumbnailSizeRegex, "$1")
        if (fullSizeUrl == originalUrl) return originalUrl

        return fullSizeUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.fragment(originalUrl)
            ?.build()
            ?.toString()
            ?: originalUrl
    }

    private val thumbnailSizeRegex = Regex("""-\d+x\d+(\.[a-zA-Z0-9]+(?:\?.*)?)$""")
    private val fileExtensionRegex = Regex("""(\.[a-zA-Z0-9]+)$""")
}
