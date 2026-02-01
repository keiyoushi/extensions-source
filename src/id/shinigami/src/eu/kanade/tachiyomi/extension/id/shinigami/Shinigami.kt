package eu.kanade.tachiyomi.extension.id.shinigami

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Shinigami : HttpSource() {
    // moved from Reaper Scans (id) to Shinigami (id)
    override val id = 3411809758861089969

    override val name = "Shinigami"

    override val baseUrl = "https://09.shinigami.asia"

    private val apiUrl = "https://api.shngm.io"

    private val cdnUrl = "https://storage.shngm.id"

    override val lang = "id"

    override val supportsLatest = true

    private val apiHeaders: Headers by lazy { apiHeadersBuilder().build() }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("X-Requested-With", randomString((1..20).random())) // added for webview, and removed in interceptor for normal use

    private fun randomString(length: Int) = buildString {
        val charPool = ('a'..'z') + ('A'..'Z')
        repeat(length) { append(charPool.random()) }
    }

    private fun apiHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", "application/json")
        .add("DNT", "1")
        .add("Origin", baseUrl)
        .add("Sec-GPC", "1")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "popularity")
            .build()

        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val rootObject = response.parseAs<ShinigamiBrowseDto>()
        val projectList = rootObject.data.map(::popularMangaFromObject)

        val hasNextPage = rootObject.meta.totalPage?.let { rootObject.meta.page < it } ?: false

        return MangasPage(projectList, hasNextPage)
    }

    private fun popularMangaFromObject(obj: ShinigamiBrowseDataDto): SManga = SManga.create().apply {
        title = obj.title!!
        thumbnail_url = obj.thumbnail
        url = obj.mangaId!!
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "latest")
            .build()

        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/v1/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        return GET(url.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

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
        Pair("Action", "action"),
        Pair("Adaptation", "adaptation"),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Cooking", "cooking"),
        Pair("Crime", "crime"),
        Pair("Demon", "demon"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Fight", "fight"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Magic", "magic"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Murim", "murim"),
        Pair("Mystery", "mystery"),
        Pair("Philosophical", "philosophical"),
        Pair("Psychological", "psychological"),
        Pair("Regression", "regression"),
        Pair("Revenge", "revenge"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
    )

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/series/${manga.url}"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Migration from old web urls to the new api based
        if (manga.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        return GET("$apiUrl/v1/manga/detail/${manga.url}", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetailsResponse = response.parseAs<ShinigamiMangaDetailDto>()
        val mangaDetails = mangaDetailsResponse.data

        return SManga.create().apply {
            author = mangaDetails.taxonomy["Author"]?.joinToString { it.name }.orEmpty()
            artist = mangaDetails.taxonomy["Artist"]?.joinToString { it.name }.orEmpty()
            status = mangaDetails.status.toStatus()
            description = mangaDetails.description

            val genres = mangaDetails.taxonomy["Genre"]?.joinToString { it.name }.orEmpty()
            val type = mangaDetails.taxonomy["Format"]?.joinToString { it.name }.orEmpty()
            genre = listOf(genres, type).filter { it.isNotBlank() }.joinToString()
        }
    }

    private fun Int.toStatus(): Int {
        return when (this) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$apiUrl/v1/chapter/${manga.url}/list?page_size=3000", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ShinigamiChapterListDto>()

        return result.chapterList.map(::chapterFromObject)
    }

    private fun chapterFromObject(obj: ShinigamiChapterListDataDto): SChapter = SChapter.create().apply {
        date_upload = dateFormat.tryParse(obj.date)
        name = "Chapter ${obj.name.toString().replace(".0","")} ${obj.title}"
        url = obj.chapterId
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Migration from old web urls to the new api based
        if (chapter.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        return GET("$apiUrl/v1/chapter/detail/${chapter.url}", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ShinigamiPageListDto>()

        return result.pageList.chapterPage.pages.mapIndexed { index, imageName ->
            Page(index = index, imageUrl = "$cdnUrl${result.pageList.chapterPage.path}$imageName")
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .add("DNT", "1")
            .add("referer", "$baseUrl/")
            .add("sec-fetch-dest", "empty")
            .add("Sec-GPC", "1")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
    }
}
