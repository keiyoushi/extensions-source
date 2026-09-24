package eu.kanade.tachiyomi.extension.es.tumangaonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Tumangaonline : HttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/biblioteca?order_item=likes_count&order_dir=desc&_pg=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.element").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null

            // Seleccionar el título desde div.thumbnail-title h4 o el atributo title, evitando etiquetas tipo "MANGA" o "DOUJINSHI"
            val titleEl = element.selectFirst("div.thumbnail-title h4, h4.text-truncate, h4")
            val rawTitle = titleEl?.attr("title")?.takeIf { it.isNotBlank() && !isBadgeText(it) }
                ?: titleEl?.text()?.trim()?.takeIf { !isBadgeText(it) }
                ?: link.attr("title").trim().takeIf { !isBadgeText(it) }
                ?: ""

            if (rawTitle.isBlank()) return@mapNotNull null

            SManga.create().apply {
                title = rawTitle
                setUrlWithoutDomain(link.attr("href"))

                val img = element.selectFirst("img.cover-bg-img") ?: element.selectFirst("img")
                val dataBg = element.selectFirst("div.thumbnail")?.attr("data-bg")

                thumbnail_url = when {
                    !dataBg.isNullOrBlank() -> dataBg
                    img != null -> img.attr("abs:src")
                    else -> null
                }
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("a[rel=next], li.page-item.active + li.page-item a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun isBadgeText(text: String): Boolean {
        val t = text.trim().uppercase()
        return t == "MANGA" || t == "MANHWA" || t == "MANHUA" || t == "DOUJINSHI" || t == "NOVEL" || t == "ONE SHOT"
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/biblioteca?order_item=creation&order_dir=desc&_pg=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("biblioteca")
            .addQueryParameter("title", query.trim())
            .addQueryParameter("_pg", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Details =========================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.element-title, h1.book-title")?.text()?.trim() ?: ""
            description = document.select("p.element-description, div.element-description, div.synopsis").text().trim()

            val authorEl = document.select("a[href*='filter_by=author'], span.author, a.ns-author-link, div.author")
            author = authorEl.map { it.text().trim() }.filter { it.isNotEmpty() }.distinct().joinToString().takeIf { it.isNotEmpty() }

            val genres = document.select("a[href*='genders[]'], a.badge-primary, a.badge-pill")
            genre = genres.joinToString { it.text().trim() }.takeIf { it.isNotEmpty() }

            val coverImg = document.selectFirst("img.book-thumbnail, img.cover, meta[property=og:image]")
            thumbnail_url = coverImg?.attr("abs:src")?.takeIf { it.isNotEmpty() } ?: coverImg?.attr("content")

            val statusText = document.select("span.book-status, span.status, div.status, span.badge-success, span.badge-info").text()
            status = parseStatus(statusText)
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Publicándose", ignoreCase = true) || status.contains("En emisión", ignoreCase = true) -> SManga.ONGOING
        status.contains("Finalizado", ignoreCase = true) || status.contains("Completado", ignoreCase = true) -> SManga.COMPLETED
        status.contains("Pausado", ignoreCase = true) -> SManga.ON_HIATUS
        status.contains("Cancelado", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ========================= Chapters =========================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterElements = document.select("li.upload-link, li.list-group-item")

        val chapters = chapterElements.mapNotNull { element ->
            // Seleccionar el enlace real al lector (/view_uploads/ID)
            val link = element.selectFirst("a[href*='/view_uploads/']")
                ?: element.selectFirst("a.btn-primary, a.chapter-link, a")
                ?: return@mapNotNull null

            val numberText = element.selectFirst("span.chapter-number")?.text()?.trim()
                ?: element.attr("data-chapter-number").takeIf { it.isNotEmpty() }?.let { "Capítulo $it" }
                ?: ""

            val titleText = element.selectFirst("span.chapter-title, span.title")?.text()?.trim() ?: ""

            // Garantizar que NUNCA aparezca "Leer online" como nombre
            val fullName = when {
                numberText.isNotEmpty() && titleText.isNotEmpty() -> "$numberText - $titleText"
                numberText.isNotEmpty() -> numberText
                titleText.isNotEmpty() -> titleText
                else -> element.selectFirst("h4")?.text()?.trim()?.takeIf { !it.equals("Leer online", ignoreCase = true) } ?: "Capítulo"
            }

            val numAttr = element.attr("data-chapter-number")
            val numVal = numAttr.toFloatOrNull()
                ?: Regex("""\d+(\.\d+)?""").find(numberText)?.value?.toFloatOrNull()
                ?: -1f

            val dateStr = element.selectFirst("span.chapter-row-date, span.date, small.text-muted")?.text()?.trim()

            SChapter.create().apply {
                name = fullName
                chapter_number = numVal
                date_upload = DATE_FORMAT.tryParse(dateStr)
                setUrlWithoutDomain(link.attr("href"))
            }
        }.distinctBy { it.url }

        // Retornar ordenados con el más reciente al principio
        return chapters.sortedByDescending { it.chapter_number }
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        val images = document.select("div.reader-img-wrap img, img.main-img, div.img-container img")

        images.forEachIndexed { index, img ->
            val imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            if (imageUrl.isNotEmpty()) {
                pages.add(Page(index, "", imageUrl))
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
