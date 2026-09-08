package eu.kanade.tachiyomi.extension.en.hentaireadio

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiReadio : KeiSource() {
    // Site is behind Cloudflare

    private val dateFormatter by lazy {
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    }

    fun parseFilteredManga(document: Document): MangasPage {
        val mangas = document.select("div.card:has(.jtip)").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst(".title-manga a")!!
                title = anchor.text()
                setUrlWithoutDomain(anchor.attr("href"))
                thumbnail_url = element.selectFirst("img.card-img-top")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li.page-item a.page-link:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/?act=search&f[status]=all&f[sortby]=top-manga&pageNum=$page").asJsoup()
        return parseFilteredManga(document)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/?act=search&f[status]=all&f[sortby]=lastest-chap&pageNum=$page").asJsoup()
        return parseFilteredManga(document)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("act", "search")
            .addQueryParameter("pageNum", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("f[keyword]", query)
        }

        var statusAdded = false
        var sortAdded = false

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    url.addQueryParameter("f[status]", filter.toUriPart())
                    statusAdded = true
                }
                is SortFilter -> {
                    url.addQueryParameter("f[sortby]", filter.toUriPart())
                    sortAdded = true
                }
                is GenreFilter -> {
                    val genre = filter.toUriPart()
                    if (genre.isNotEmpty()) {
                        url.addQueryParameter("f[genres]", genre)
                    }
                }
                else -> {}
            }
        }

        if (!statusAdded) url.addQueryParameter("f[status]", "all")
        if (!sortAdded) url.addQueryParameter("f[sortby]", "lastest-chap")

        val document = client.get(url.build()).asJsoup()
        return parseFilteredManga(document)
    }

    // ============================== Details ===============================

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.title-detail")!!.text()
        author = document.selectFirst(".author p.col-8")?.text()
            ?.takeUnless { it.contains("Updating", ignoreCase = true) }
        status = parseStatus(document.selectFirst(".status p.col-8")?.text())
        genre = document.select(".kind p.col-8 a").joinToString(", ") { it.text() }
        description = document.selectFirst("#summary_shortened")?.text()
        thumbnail_url = document.selectFirst(".col-image img")?.absUrl("src")
        initialized = true
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga {
        if (url.host != baseUrl.toHttpUrl().host) {
            throw Exception("Unsupported URL")
        }

        if (
            url.pathSegments.size !in 1..2 ||
            url.pathSegments[0].isBlank() ||
            url.pathSegments.getOrNull(1)?.isNotBlank() == true
        ) {
            throw Exception("Unsupported URL")
        }

        val document = client.get(url).asJsoup()
        return parseMangaDetails(document).apply { setUrlWithoutDomain(url.toString()) }
    }

    private fun parseStatus(status: String?) = when (status?.trim()?.lowercase()) {
        "complete", "completed" -> SManga.COMPLETED
        "in process", "ongoing" -> SManga.ONGOING
        "pause", "on hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("ul#list_chapter_id_detail li.wp-manga-chapter, ul.version-chap li.wp-manga-chapter")
        .map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.attr("href"))
                name = link.text()
                date_upload = runCatching {
                    element.selectFirst(".chapter-release-date i")?.text()?.trim()?.let {
                        LocalDate.parse(it, dateFormatter)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli()
                    }
                }.getOrNull() ?: 0L
            }
        }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select(".page-chapter img").mapIndexed { index, img ->
            // Prefer data-src (lazy-loaded) over src which may be a placeholder
            val url = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
            Page(index, "", url)
        }
    }

    // =============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = getFilters()
}
