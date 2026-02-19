package eu.kanade.tachiyomi.extension.id.softkomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class Softkomik : HttpSource() {
    override val name = "Softkomik"
    override val baseUrl = "https://softkomik.com"
    override val lang = "id"
    override val supportsLatest = true

    companion object {
        private const val COVER_URL = "https://cover.softdevices.my.id/softkomik-cover"
        private const val IMAGE_URL = "https://image.softkomik.com/softkomik"
        private const val CHAPTER_URL = "https://v2.softdevices.my.id"
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::buildIdOutdatedInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    @Volatile
    private var buildId = ""
        get() = field.ifEmpty {
            synchronized(this) {
                field.ifEmpty { fetchBuildId().also { field = it } }
            }
        }

    private fun fetchBuildId(document: Document? = null): String {
        val doc = document
            ?: client.newCall(GET(baseUrl, headers)).execute().use { it.asJsoup() }
        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw Exception("Could not find __NEXT_DATA__")
        return nextData.parseAs<NextDataDto>().buildId
    }

    private fun buildIdOutdatedInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (
            response.code == 404 &&
            request.url.run {
                host == baseUrl.removePrefix("https://") &&
                    pathSegments.getOrNull(0) == "_next" &&
                    pathSegments.getOrNull(1) == "data" &&
                    fragment != "DO_NOT_RETRY"
            } &&
            response.header("Content-Type")?.contains("text/html") != false
        ) {
            val document = response.asJsoup()
            val newBuildId = fetchBuildId(document)
            synchronized(this) { buildId = newBuildId }

            val newUrl = request.url.newBuilder()
                .setPathSegment(2, newBuildId)
                .fragment("DO_NOT_RETRY")
                .build()
            return chain.proceed(request.newBuilder().url(newUrl).build())
        }
        return response
    }

    // ======================== Popular ========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/_next/data/$buildId/komik/library.json".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/_next/data/$buildId/komik/library.json".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "newKomik")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ======================== Search ========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/_next/data/$buildId/komik/list.json".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("page", page.toString())
        } else {
            "$baseUrl/_next/data/$buildId/komik/library.json".toHttpUrl().newBuilder()
                .addQueryParameter("search", "")
                .addQueryParameter("page", page.toString())
        }

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> url.addQueryParameter("status", filter.selected)
                is TypeFilter -> url.addQueryParameter("type", filter.selected)
                is GenreFilter -> url.addQueryParameter("genre", filter.selected)
                is SortFilter -> url.addQueryParameter("sortBy", filter.selected)
                is MinChapterFilter -> url.addQueryParameter("min", filter.selected)
                else -> {}
            }
        }

        if (query.isNotEmpty()) {
            url.setQueryParameter("sortBy", "")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val isList = response.request.url.pathSegments.contains("list.json")
        return if (isList) {
            val dto = response.parseAs<ListDto>()
            val libData = dto.pageProps.data
            val mangas = libData.data.map { manga ->
                SManga.create().apply {
                    setUrlWithoutDomain(manga.title_slug)
                    title = manga.title
                    thumbnail_url = "$COVER_URL/${manga.gambar.removePrefix("/")}"
                }
            }
            MangasPage(mangas, libData.page < libData.maxPage)
        } else {
            val dto = response.parseAs<LibraryDto>()
            val mangas = dto.pageProps.libData.data.map { manga ->
                SManga.create().apply {
                    setUrlWithoutDomain(manga.title_slug)
                    title = manga.title
                    thumbnail_url = "$COVER_URL/${manga.gambar.removePrefix("/")}"
                }
            }
            MangasPage(mangas, dto.pageProps.libData.page < dto.pageProps.libData.maxPage)
        }
    }

    // ======================== Details ========================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/_next/data/$buildId/${manga.url}.json", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<DetailsDto>()
        val manga = dto.pageProps.data
        val slug = response.request.url.pathSegments.lastOrNull()!!.removeSuffix(".json")
        return SManga.create().apply {
            setUrlWithoutDomain(slug)
            title = manga.title
            author = manga.author
            description = manga.sinopsis
            genre = manga.Genre?.joinToString()
            status = when (manga.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "tamat" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = "$COVER_URL/${manga.gambar.removePrefix("/")}"
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    // ======================== Chapters ========================
    override fun chapterListRequest(manga: SManga): Request = GET("$CHAPTER_URL/komik/${manga.url}/chapter?limit=9999999", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ChapterListDto>()
        val slug = response.request.url.pathSegments[1]
        return dto.chapter.map { chapter ->
            val chapterNum = chapter.chapter.toFloatOrNull() ?: -1f
            val displayNum = formatChapterDisplay(chapter.chapter)
            SChapter.create().apply {
                url = "/$slug/chapter/${chapter.chapter}"
                name = "Chapter $displayNum"
                chapter_number = chapterNum
            }
        }.sortedByDescending { it.chapter_number }
    }

    private fun formatChapterDisplay(chapterStr: String): String {
        val floatVal = chapterStr.toFloatOrNull() ?: return chapterStr
        return if (floatVal == floatVal.toLong().toFloat()) {
            floatVal.toLong().toString()
        } else {
            floatVal.toString().trimEnd('0').trimEnd('.')
        }
    }

    // ======================== Pages ========================
    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw Exception("Could not find __NEXT_DATA__")
        val dto = nextData.parseAs<ChapterPageDto>()
        val images = dto.props.pageProps.data.data.imageSrc

        return images.mapIndexed { i, img ->
            Page(i, imageUrl = "$IMAGE_URL/$img")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .set("Origin", baseUrl)
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filter tidak bisa digabungkan dengan pencarian teks."),
        Filter.Separator(),
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
        MinChapterFilter(),
    )
}
