package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class KomikCast : HttpSource() {

    override val id = 972717448578983812

    override val name = "Komik Cast"

    override val baseUrl = "https://v1.komikcast03.com"

    private val apiUrl = "https://be.komikcast.to"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json")
        .add("Accept-language", "en-US,en;q=0.9,id;q=0.8")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("sortOrder", "desc")
            .addQueryParameter("take", "12")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseSeriesListResponse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "latest")
            .addQueryParameter("sortOrder", "desc")
            .addQueryParameter("take", "12")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseSeriesListResponse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("take", "12")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("filter", "title=like=\"$query\",nativeTitle=like=\"$query\"")
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSeriesListResponse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/series/").substringBefore("/")
        return GET("$apiUrl/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesDetailResponse>()
        return result.data.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/series/").substringBefore("/")
        return GET("$apiUrl/series/$slug/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterListResponse>()
        val slug = response.request.url.pathSegments.getOrNull(1) ?: ""
        return result.data.map { it.toSChapter(slug) }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val urlParts = chapter.url.substringAfter("/series/").split("/")
        val slug = urlParts.getOrNull(0) ?: ""
        val chapterIndex = urlParts.getOrNull(2) ?: ""
        return GET("$apiUrl/series/$slug/chapters/$chapterIndex", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterDetailResponse>()
        val images = result.data.dataImages?.toSortedMap(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })
            ?.values?.toList() ?: emptyList()
        return images.mapIndexed { index, imageUrl ->
            Page(index, "", imageUrl)
        }
    }

    private fun parseSeriesListResponse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.meta?.let { it.page ?: 0 < (it.lastPage ?: 0) } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            SortFilter(),
            SortOrderFilter(),
            StatusFilter(),
            FormatFilter(),
            TypeFilter(),
            GenreFilter(getGenres()),
        )
    }

    private fun getGenres(): Array<Pair<String, String>> = arrayOf(
        Pair("4-Koma", "4-Koma"),
        Pair("Adventure", "Adventure"),
        Pair("Cooking", "Cooking"),
        Pair("Game", "Game"),
        Pair("Gore", "Gore"),
        Pair("Harem", "Harem"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Isekai", "Isekai"),
        Pair("Josei", "Josei"),
        Pair("Magic", "Magic"),
        Pair("Martial Arts", "Martial Arts"),
        Pair("Mature", "Mature"),
        Pair("Mecha", "Mecha"),
        Pair("Medical", "Medical"),
        Pair("Military", "Military"),
        Pair("Music", "Music"),
        Pair("Mystery", "Mystery"),
        Pair("One-Shot", "One-Shot"),
        Pair("Police", "Police"),
        Pair("Psychological", "Psychological"),
        Pair("Reincarnation", "Reincarnation"),
        Pair("Romance", "Romance"),
        Pair("School", "School"),
        Pair("School Life", "School Life"),
        Pair("Sci-Fi", "Sci-Fi"),
        Pair("Seinen", "Seinen"),
        Pair("Shoujo", "Shoujo"),
        Pair("Shoujo Ai", "Shoujo Ai"),
        Pair("Action", "Action"),
        Pair("Comedy", "Comedy"),
        Pair("Demons", "Demons"),
        Pair("Drama", "Drama"),
        Pair("Ecchi", "Ecchi"),
        Pair("Fantasy", "Fantasy"),
        Pair("Gender Bender", "Gender Bender"),
        Pair("Shounen", "Shounen"),
        Pair("Shounen Ai", "Shounen Ai"),
        Pair("Slice of Life", "Slice of Life"),
        Pair("Sports", "Sports"),
        Pair("Super Power", "Super Power"),
        Pair("Supernatural", "Supernatural"),
        Pair("Thriller", "Thriller"),
        Pair("Tragedy", "Tragedy"),
        Pair("Vampire", "Vampire"),
        Pair("Webtoons", "Webtoons"),
        Pair("Yuri", "Yuri"),
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
