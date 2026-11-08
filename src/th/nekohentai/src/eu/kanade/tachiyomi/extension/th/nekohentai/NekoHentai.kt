package eu.kanade.tachiyomi.extension.th.nekohentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZonedDateTime

@Source
abstract class NekoHentai : KeiSource() {

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { !it.encodedPath.startsWith("/uploads/") }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".bg-top div.col-sm-12").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".sr-title-sm a")!!
                url = slug(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst(".img-top-image")?.backgroundImage()
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = listing(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val keyword = query.trim()
            val body = FormBody.Builder().add("keysearch", keyword).build()
            val searchPage = "$baseUrl/search/".toHttpUrl().newBuilder().addQueryParameter("s", keyword).build()
            val document = client.post("$baseUrl/controller/search.php", Fetch.XHR.from(searchPage), body).asJsoup()
            return MangasPage(document.select("div.item-grid").map(::mangaFromCard), false)
        }

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }
        if (genres.size > 1) {
            val body = FormBody.Builder().apply { genres.forEach { add("filter[]", it.id) } }.build()
            val document = client.post("$baseUrl/filter/", Fetch.DOCUMENT.from(baseUrl), body).asJsoup()
            return MangasPage(document.select("div.item-grid").map(::mangaFromCard), false)
        }

        val category = filters.firstInstanceOrNull<CategoryFilter>()?.value
        return when {
            genres.size == 1 -> listing(page, "genre", genres[0].name)
            category != null -> listing(page, "category", category)
            else -> listing(page)
        }
    }

    private suspend fun listing(page: Int, vararg path: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            // The site's Apache refuses %20 in these paths
            path.forEach { addPathSegment(it.replace(' ', '+')) }
            if (path.isNotEmpty()) addPathSegment("")
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val hasNextPage = document.selectFirst("select.form-control option[selected] + option") != null
        return MangasPage(document.select("div.item-grid").map(::mangaFromCard), hasNextPage)
    }

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        val link = element.selectFirst("a")!!
        url = slug(link.absUrl("href"))
        title = link.selectFirst("h2")!!.text()
        thumbnail_url = link.selectFirst(".poster")?.backgroundImage()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga) = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("div.item-grid")
        .map(::mangaFromCard)

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 2 || segments[0] !in LISTING_PATHS) {
            return super.getMangasByUrl(url, page)
        }
        return listing(page, segments[0], segments[1])
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size !in 1..2 || segments[0] in RESERVED_PATHS) return null
        val manga = SManga.create().apply { this.url = segments[0] }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        val details = mangaDetails(manga, document)
        // One-shots have their reader on the details page instead of a chapter list
        if (document.selectFirst("#manga-content") != null) {
            details.status = SManga.COMPLETED
            details.update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            val chapter = SChapter.create().apply {
                url = manga.url
                name = document.selectFirst("div.heading:has(i.fa-star) h2")!!.text()
                date_upload = parseRelativeDate(document.selectFirst(".sr-post-header")?.text()?.substringAfter("อัพเดทเมื่อ ")?.substringBefore(" /"))
            }
            return SMangaUpdate(details, listOf(chapter))
        }
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val lastPage = document.select("select.form-control option")
            .maxOfOrNull { it.absUrl("value").toHttpUrl().queryParameter("page")!!.toInt() } ?: 1
        val otherPages = coroutineScope {
            (2..lastPage).map { page ->
                async {
                    val url = mangaUrl.toHttpUrl().newBuilder().addQueryParameter("page", page.toString()).build()
                    client.get(url, Fetch.DOCUMENT.from(mangaUrl)).asJsoup()
                }
            }.awaitAll()
        }
        val chapterList = (listOf(document) + otherPages).flatMap { it.select("div.ep-row") }.map(::chapterFromRow)
        return SMangaUpdate(details, chapterList.reversed())
    }

    private fun mangaDetails(manga: SManga, document: Document) = manga.apply {
        title = document.selectFirst("h1")!!.ownText()
        thumbnail_url = document.selectFirst("img.img-thumbnail")?.absUrl("src")
        val info = document.select("div.col-md-9 > div.row.mt-3").mapNotNull { row ->
            row.selectFirst(".col-2")?.let { it.text() to row.select(".col-9 a") }
        }.toMap()
        artist = info["ศิลปิน"]?.map { it.text() }?.filterNot { it in PLACEHOLDER_ARTISTS }?.joinToString()?.ifEmpty { null }
        author = artist
        genre = (info["หมวดหมู่"].orEmpty() + info["ประเภท"].orEmpty()).map { it.text() }.distinct().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .field("เผยแพร่ครั้งแรกปี", info["เผยแพร่ครั้งแรกปี"]?.text()?.takeUnless { it == "0" })
            .build()
    }

    private fun chapterFromRow(row: Element) = SChapter.create().apply {
        val link = row.selectFirst("a")!!
        val segments = link.absUrl("href").toHttpUrl().pathSegments.filter { it.isNotEmpty() }
        url = segments.joinToString("/")
        name = link.text()
        date_upload = parseRelativeDate(row.selectFirst("> div:last-child")?.text())
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#manga-content img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl(if (img.hasAttr("data-src")) "data-src" else "src")) }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return FilterDataDto(
            categories = document.select("a.nav-link[href*=/category/]").map { it.text() },
            genres = document.select("#tags label.item").map { GenreDto(it.selectFirst("input")!!.attr("value"), it.text()) },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterDataDto>() ?: return FilterList()
        return FilterList(
            Filter.Header("Text search ignores filters, and genres ignore the category"),
            CategoryFilter(filterData.categories),
            GenreFilter(filterData.genres),
        )
    }

    private fun Element.backgroundImage() = BACKGROUND_IMAGE_REGEX.find(attr("style"))?.groupValues?.get(1)

    private fun slug(url: String) = url.toHttpUrl().pathSegments.first { it.isNotEmpty() }

    private fun parseRelativeDate(date: String?): Long {
        val amount = date?.substringBefore(" ")?.toLongOrNull() ?: return 0L
        val now = ZonedDateTime.now()
        val then = when {
            "วินาที" in date -> now.minusSeconds(amount)
            "นาที" in date -> now.minusMinutes(amount)
            "ชั่วโมง" in date -> now.minusHours(amount)
            "วัน" in date -> now.minusDays(amount)
            "สัปดาห์" in date -> now.minusWeeks(amount)
            "เดือน" in date -> now.minusMonths(amount)
            "ปี" in date -> now.minusYears(amount)
            else -> return 0L
        }
        return then.toInstant().toEpochMilli()
    }

    companion object {
        private val BACKGROUND_IMAGE_REGEX = Regex("""url\(([^)]+)\)""")

        // The site's artists for doujins with no credited artist
        private val PLACEHOLDER_ARTISTS = setOf("Unknown", "Unknows")

        private val LISTING_PATHS = setOf("artist", "category", "genre", "year")

        private val RESERVED_PATHS = LISTING_PATHS + setOf("controller", "filter", "register", "search", "uploads")
    }
}
