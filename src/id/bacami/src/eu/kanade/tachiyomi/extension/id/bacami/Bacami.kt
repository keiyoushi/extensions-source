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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Bacami : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.ENGLISH)
    private val chapterRegex = Regex("""(?:Chapter|Ch\.)\s+([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("custom-search/orderby/score")
            if (page > 1) {
                addPathSegments("page/$page")
            }
        }.build()
        return mangaListParse(client.get(url).asJsoup())
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("custom-search/orderby/latest")
            if (page > 1) {
                addPathSegments("page/$page")
            }
        }.build()
        return mangaListParse(client.get(url).asJsoup())
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addPathSegment(query)
                if (page > 1) {
                    addPathSegments("page/$page")
                }
            }.build()
        } else {
            val isNewKomik = filters.firstInstanceOrNull<NewKomikFilter>()?.state == true
            if (isNewKomik) {
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("komik-baru")
                    if (page > 1) {
                        addPathSegments("page/$page")
                    }
                }.build()
            } else {
                val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: "all"
                val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: "all"
                val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: "all"
                val orderby = filters.firstInstanceOrNull<OrderByFilter>()?.toUriPart() ?: "latest"

                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("custom-search")
                    if (genre != "all") addPathSegments("genre/$genre")
                    if (status != "all") addPathSegments("status/$status")
                    if (type != "all") addPathSegments("type/$type")
                    if (orderby != "latest") addPathSegments("orderby/$orderby")
                    if (page > 1) addPathSegments("page/$page")
                }.build()
            }
        }

        return mangaListParse(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = runCatching {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.none { it.isNotEmpty() }) return null

        val mangaPath = if (url.pathSegments.firstOrNull() == "komik") {
            url.encodedPath
        } else {
            val document = client.get(url).asJsoup()
            val href = document.selectFirst("div.allc a[href*='/komik/'], a.midall[href*='/komik/'], .breadcrumb a[href*='/komik/']")?.absUrl("href")
                ?: return null
            href.toHttpUrl().encodedPath
        }

        val manga = SManga.create().apply {
            setUrlWithoutDomain(mangaPath)
        }
        getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.apply {
            initialized = true
        }
    }.getOrNull()

    // ======================= Details and Chapters ==========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document).apply {
                url = manga.url
                if (title.isEmpty()) {
                    title = manga.title
                }
            },
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        val content = document.selectFirst("#komik > section.manga-content") ?: return@apply

        title = content.selectFirst("header > h1")?.text()?.removeSuffix("Bahasa Indonesia")?.trim().orEmpty()
        thumbnail_url = content.selectFirst("figure .image-wrap img")?.imgAttr()
        author = content.selectFirst(".info-item:contains(Author) .info-value")?.text()
            ?.ifEmpty { content.selectFirst("div > div > div:nth-child(3) > span.info-value")?.text() }
        genre = content.select("nav > span > a").joinToString { it.text() }
        status = parseStatus(document)

        val altTitle = content.select("p.manga-altname").text()
        val desc = content.select("p.manga-description").text()
        description = if (altTitle.isNotEmpty()) {
            if (desc.isNotEmpty()) "$desc\n\nAlternative Title: $altTitle" else "Alternative Title: $altTitle"
        } else {
            desc
        }
        initialized = true
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
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val scriptContent = document.selectFirst("script:containsData(imageUrls)")?.data()
            ?: return emptyList()

        val jsonString = scriptContent.substringAfter("imageUrls:").substringBefore("],").plus("]")
        val imageUrls = jsonString.parseAs<List<String>>()
        return imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
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
    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("article.genre-card").map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("div.paginate a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.genre-info > a")!!.text()
        setUrlWithoutDomain(element.selectFirst("div.genre-cover > a")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("div.genre-cover > a > img")?.imgAttr()
    }

    private fun Element.imgAttr(): String = absUrl("data-src").ifEmpty { absUrl("src") }
}
