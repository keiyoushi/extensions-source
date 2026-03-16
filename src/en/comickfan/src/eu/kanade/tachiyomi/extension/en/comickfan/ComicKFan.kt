package eu.kanade.tachiyomi.extension.en.comickfan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ComicKFan : HttpSource() {

    override val name = "ComicK Fanmade"
    override val baseUrl = "https://comickfan.com"
    override val lang = "en"
    override val supportsLatest = true

    override val client = network.cloudflareClient
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT)

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", SortFilter.POPULAR)
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", SortFilter.LATEST)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val slug = query.toHttpUrlOrNull()
                ?.pathSegments
                ?.getOrNull(1)
                ?: throw Exception("Invalid URL")

            // Rewrite to strip suffixes after slug
            val newUrl = "$baseUrl/manga/$slug"
            return fetchMangaDetails(SManga.create().apply { setUrlWithoutDomain(newUrl) })
                .map { manga -> MangasPage(listOf(manga), hasNextPage = false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = buildList {
            addAll(filters.filterIsInstance<FormatGenreFilter>().firstOrNull()?.selected.orEmpty())
            addAll(filters.filterIsInstance<ContentGenreFilter>().firstOrNull()?.selected.orEmpty())
            addAll(filters.filterIsInstance<ThemeGenreFilter>().firstOrNull()?.selected.orEmpty())
            addAll(filters.filterIsInstance<GenreGenreFilter>().firstOrNull()?.selected.orEmpty())
        }.joinToString("_")

        val status = filters.filterIsInstance<StatusFilter>().firstOrNull()?.toUriPart().orEmpty()
        val type = filters.filterIsInstance<TypeFilter>().firstOrNull()?.toUriPart().orEmpty()
        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.toUriPart().orEmpty()

        val url = "$baseUrl/advanced-search".toHttpUrl().newBuilder()
            .addQueryParameter("genres", genres)
            .addQueryParameter("status", status)
            .addQueryParameter("type", type)
            .addQueryParameter("sort", sort)
            .addQueryParameter("name", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("div:has(> form) + div.grid > a")
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst("a:has(img[alt=Next])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))

        val img = element.selectFirst("img")!!
        title = img.attr("alt")
        thumbnail_url = img.absUrl("src")
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoRoot = document.selectFirst("div[class=bg-card-section]")

        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("div.comic-content.desk")?.text()
            author = infoRoot?.getValue("Author")?.split(",")?.joinToString()
            artist = infoRoot?.getValue("Artist")?.split(",")?.joinToString()
            genre = infoRoot?.select("div.font-medium:contains(Genres) + div a")?.joinToString(transform = Element::text)

            status = when (infoRoot?.getValue("Status")?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                // "cancelled" -> SManga.CANCELLED // Shows as '❓ Unknown'
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = infoRoot?.selectFirst("div.thumb-cover img")?.absUrl("src")
        }
    }

    private fun Element.getValue(label: String): String? = select("div.flex-row.gap-4")
        .firstOrNull { it.selectFirst("> div.text-sm")?.text()?.equals(label) == true }
        ?.selectFirst("> div.text-sm:nth-child(2):last-child")
        ?.takeIf { it.text() !in listOf("", "-", "_") }
        ?.text()

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val comicId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.getOrNull(1)
            ?: throw Exception("Invalid manga URL: ${manga.url}")

        return GET("$baseUrl/api/comics/$comicId/chapter-list?translation_group_id=", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val comicId = response.request.url.pathSegments.getOrNull(2)
            ?: throw Exception("Unable to parse comic id from chapter API URL")

        return response.parseAs<ComicKFanChapterListResponseDto>().data
            .map { it.toSChapter(comicId) }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = document.select("div.w-full > img[loading=lazy]")
        return pages.mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        FormatGenreFilter(),
        ContentGenreFilter(),
        ThemeGenreFilter(),
        GenreGenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )

    private fun ComicKFanChapterDto.toSChapter(comicId: String) = SChapter.create().apply {
        setUrlWithoutDomain("/manga/$comicId/chapter-$chapter-$hashId")
        name = "Chapter $chapter"
        scanlator = groupNames.joinToString()
        chapter.toFloatOrNull()?.also { chapter_number = it }
        date_upload = dateFormat.tryParse(publishedAt ?: createdAt)
    }
}
