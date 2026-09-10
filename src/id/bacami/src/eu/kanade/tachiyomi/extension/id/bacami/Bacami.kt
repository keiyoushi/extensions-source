package eu.kanade.tachiyomi.extension.id.bacami

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Bacami : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.ENGLISH)
    private val chapterRegex = Regex("""(?:Chapter|Ch\.)\s+([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/custom-search/orderby/score/${pagePath(page)}".toHttpUrl()
        val response = client.get(url, ensureSuccess = false)
        if (response.code == 404) return MangasPage(emptyList(), false)
        return mangaListParse(response)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/custom-search/orderby/latest/${pagePath(page)}".toHttpUrl()
        val response = client.get(url, ensureSuccess = false)
        if (response.code == 404) return MangasPage(emptyList(), false)
        return mangaListParse(response)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addPathSegment(query)
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addPathSegment("")
            }.build()
        } else {
            val isNewKomik = filters.firstInstanceOrNull<NewKomikFilter>()?.state == true
            if (isNewKomik) {
                "$baseUrl/komik-baru/${pagePath(page)}".toHttpUrl()
            } else {
                val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: "all"
                val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: "all"
                val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: "all"
                val orderby = filters.firstInstanceOrNull<OrderByFilter>()?.toUriPart() ?: "latest"

                val urlString = buildString {
                    append("$baseUrl/custom-search/")
                    if (genre != "all") append("genre/$genre/")
                    if (status != "all") append("status/$status/")
                    if (type != "all") append("type/$type/")
                    if (orderby != "latest") append("orderby/$orderby/")
                    if (page > 1) append("page/$page/")
                }
                urlString.toHttpUrl()
            }
        }

        val response = client.get(url, ensureSuccess = false)
        if (response.code == 404) return MangasPage(emptyList(), false)
        return mangaListParse(response)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "komik") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val targetUrl = "$baseUrl/komik/$slug/".toHttpUrl()
        val document = client.get(targetUrl).asJsoup()
        val manga = SManga.create().apply {
            setUrlWithoutDomain(targetUrl.toString())
        }
        return parseDetails(document, manga)
    }

    // ======================= Details and Chapters ==========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document, manga),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = manga.apply {
        val content = document.selectFirst("#komik > section.manga-content") ?: return@apply

        if (title.isEmpty()) {
            val parsedTitle = content.selectFirst("header > h1")?.text()?.removeSuffix("Bahasa Indonesia")?.trim()
            if (!parsedTitle.isNullOrEmpty()) {
                title = parsedTitle
            }
        }

        thumbnail_url = content.selectFirst("figure .image-wrap img")?.imgAttr()
        author = content.selectFirst(".info-item:contains(Author) .info-value")?.text()
            ?.ifEmpty { content.selectFirst("div > div > div:nth-child(3) > span.info-value")?.text() }
        genre = content.select("nav > span > a").joinToString { it.text() }
        status = parseStatus(document)

        val altTitle = content.select("p.manga-altname").text().trim()
        val desc = content.select("p.manga-description").text().trim()
        description = if (altTitle.isNotEmpty()) {
            if (desc.isNotEmpty()) "$desc\n\nAlternative Title: $altTitle" else "Alternative Title: $altTitle"
        } else {
            desc
        }
    }

    private fun parseStatus(document: Document): Int = when {
        document.selectFirst(".hot-tag, .project-tag") != null -> SManga.ONGOING
        document.selectFirst(".tamat-tag") != null -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("ol.chapter-list > li").map { element ->
        val link = element.selectFirst("a.ch-link")!!
        SChapter.create().apply {
            val rawName = link.text()
            name = rawName.substringAfter("–").substringAfter("-").trim().ifEmpty { rawName }
            setUrlWithoutDomain(link.absUrl("href"))
            chapterRegex.find(name)?.let {
                chapter_number = it.groupValues[1].toFloatOrNull() ?: -1f
            }
            date_upload = dateFormat.tryParseDate(element.select("span.ch-date").text(), ZoneId.of("Asia/Jakarta"))
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = baseUrl + chapter.url
        val document = client.get(chapterUrl).asJsoup()
        val scriptContent = document.selectFirst("script:containsData(imageUrls)")?.data()
            ?: return emptyList()

        val jsonString = scriptContent.substringAfter("imageUrls:").substringBefore("],").plus("]")
        val imageUrls = jsonString.parseAs<List<String>>()
        return imageUrls.mapIndexed { index, url ->
            Page(index, chapterUrl, imageUrl = url)
        }
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filter diabaikan jika menggunakan pencarian teks."),
        Filter.Separator(),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        OrderByFilter(),
        Filter.Separator(),
        Filter.Header("Centang 'Komik Baru' akan mengabaikan filter lain."),
        NewKomikFilter(),
    )

    // ============================= Utilities ==============================
    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.genre-card").map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("div.paginate a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.genre-info > a")?.text().orEmpty()
        element.selectFirst("div.genre-cover > a")?.let {
            setUrlWithoutDomain(it.absUrl("href"))
        }
        thumbnail_url = element.selectFirst("div.genre-cover > a > img")?.imgAttr()
    }

    private fun Element.imgAttr(): String = absUrl("data-src").ifEmpty { absUrl("src") }
}
