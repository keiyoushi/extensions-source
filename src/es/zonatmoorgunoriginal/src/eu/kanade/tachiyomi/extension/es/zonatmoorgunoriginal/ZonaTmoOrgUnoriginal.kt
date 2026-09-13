package eu.kanade.tachiyomi.extension.es.zonatmoorgunoriginal

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ZonaTmoOrgUnoriginal : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
    }

    private val ajaxHeaders: Headers
        get() = headersBuilder()
            .set("Referer", "$baseUrl/biblioteca")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, order = "likes_count")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, order = "release_date")

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = getMangaList(page, query, order = "likes_count")

    private suspend fun getMangaList(
        page: Int,
        query: String? = null,
        order: String,
    ): MangasPage {
        val url =
            "$baseUrl/biblioteca"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("title", query?.trim().orEmpty())
                .addQueryParameter("filter_by", "title")
                .addQueryParameter("order_item", order)
                .addQueryParameter("order_dir", "desc")
                .addQueryParameter("_pg", "1")
                .addQueryParameter("page", page.toString())
                .build()

        val html = client.get(url, ajaxHeaders).parseAs<JsonObject>().getString("html")
        val document = Jsoup.parse(html, baseUrl)
        val mangas = document.select("#library-grid .element").mapNotNull(::mangaFromElement)
        val hasNextPage =
            document.selectFirst(
                "a[rel=next], .pagination a:contains(Siguiente), .pagination a:contains(›)",
            ) != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("a[href*=/library/]") ?: return null
        val title =
            link
                .selectFirst(".thumbnail-title h4")
                ?.let { it.attr("title").ifBlank(it::text) }
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(link.attr("abs:href"))
            this.title = title
            thumbnail_url =
                link
                    .selectFirst("img.cover-bg-img")
                    ?.attr("abs:src")
                    ?.ifBlank { link.selectFirst(".thumbnail.book")?.attr("data-bg") }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val sourceHost = baseUrl.toHttpUrl().host
        if (url.host.removePrefix("www.") != sourceHost.removePrefix("www.") ||
            url.pathSegments.firstOrNull() != "library"
        ) {
            throw Exception("URL no soportada")
        }

        return parseMangaDetails(client.get(url).asJsoup()).apply {
            setUrlWithoutDomain(url.toString())
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.element-title")!!.text()
        thumbnail_url = document.selectFirst("img.book-thumbnail")?.attr("abs:src")
        description = document.selectFirst("#manga-synopsis")?.text()?.trim()
        genre =
            document
                .select(".element-header-content-text a[href*=/tag/]")
                .joinToString { it.text().trim() }
                .ifBlank { null }
        author = subtitleValues(document, "Autor/es").joinToString().ifBlank { null }
        artist = subtitleValues(document, "Artista/s").joinToString().ifBlank { null }
        status =
            when (subtitleText(document, "Estado").lowercase(Locale.ROOT)) {
                "en emisión", "en emision", "publicándose", "publicandose" -> SManga.ONGOING
                "completado", "finalizado" -> SManga.COMPLETED
                "en pausa", "hiatus" -> SManga.ON_HIATUS
                "cancelado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        initialized = true
    }

    private fun subtitleValues(
        document: Document,
        label: String,
    ): List<String> {
        val heading =
            document
                .select("h5.element-subtitle")
                .firstOrNull { it.text().trim().equals(label, true) }
                ?: return emptyList()
        val values = mutableListOf<String>()
        var sibling = heading.nextElementSibling()
        while (sibling != null && !sibling.`is`("h5.element-subtitle")) {
            values += sibling.select("a").map { it.text().trim() }.filter(String::isNotEmpty)
            sibling = sibling.nextElementSibling()
        }
        return values.distinct()
    }

    private fun subtitleText(
        document: Document,
        label: String,
    ): String {
        val heading =
            document
                .select("h5.element-subtitle")
                .firstOrNull { it.text().trim().equals(label, true) }
                ?: return ""
        return heading
            .nextElementSibling()
            ?.text()
            ?.trim()
            .orEmpty()
    }

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("li.upload-link")
        .flatMap { row ->
            val numberText =
                row.attr("data-chapter-number").ifBlank {
                    row.selectFirst(".chapter-number")?.attr("data-number").orEmpty()
                }
            val date = dateFormat.tryParseDate(
                row
                    .selectFirst(".text-muted.small")
                    ?.text()
                    ?.substringAfterLast(" "),
            )

            row.select(".chapter-detail a[href*=/view_uploads/]").map { link ->
                SChapter.create().apply {
                    setUrlWithoutDomain(link.attr("abs:href"))
                    name = "Capítulo $numberText"
                    chapter_number = numberText.toFloatOrNull() ?: -1f
                    scanlator =
                        link
                            .parent()
                            ?.selectFirst("a[href*=/groups/]")
                            ?.text()
                            ?.trim()
                    date_upload = date
                }
            }
        }.distinctBy { it.url }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get(baseUrl + chapter.url)
        .asJsoup()
        .select("#reader-wrap img.reader-image")
        .mapIndexedNotNull { index, image ->
            image
                .attr("abs:src")
                .takeIf(String::isNotBlank)
                ?.let { Page(index, imageUrl = it) }
        }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}
