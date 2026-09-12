package eu.kanade.tachiyomi.extension.es.lectortmo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Lectortmo : HttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-type/manga/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga-card, article").mapNotNull { element ->
            val link = element.selectFirst("a[href*='/manga/']:not([href*='/manga-chapter/'])")
                ?: element.parent()?.takeIf { it.tagName() == "a" && it.attr("href").contains("/manga/") && !it.attr("href").contains("/manga-chapter/") }
                ?: return@mapNotNull null

            val titleEl = element.selectFirst("h3.manga-title, h2, h3")

            SManga.create().apply {
                title = titleEl?.text()?.trim() ?: link.text().trim()
                setUrlWithoutDomain(link.attr("href"))

                val styleAttr = element.attr("style")
                val bgMatch = Regex("""--manga-bg:\s*url\(['"]?([^'"]+)['"]?\)""").find(styleAttr)
                val img = element.selectFirst("img")

                thumbnail_url = when {
                    bgMatch != null -> bgMatch.groupValues[1]
                    img != null -> img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
                    else -> ""
                }?.replace(Regex("https?://(cdn\\.)?myanimelist\\.net"), "https://cdn.myanimelist.net")
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("a.next, a.next-page, link[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val document = client.newCall(GET("$baseUrl/", headers)).execute().asJsoup()
        val nonce = document.selectFirst(".mnx-search-wrap")?.attr("data-nonce") ?: ""

        val formBody = FormBody.Builder()
            .add("action", "manganexus_search_autocomplete")
            .add("nonce", nonce)
            .add("q", query.trim())
            .add("post_type", "manga")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val responseStr = response.body.string()
        val root = Json.parseToJsonElement(responseStr).jsonObject
        val data = root["data"]?.jsonObject
        val items = data?.get("items")?.jsonArray

        val mangas = items?.mapNotNull { item ->
            val itemObj = item.jsonObject
            val permalink = itemObj["permalink"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SManga.create().apply {
                title = itemObj["title"]?.jsonPrimitive?.content?.replace("&#039;", "'")?.replace("&amp;", "&") ?: "Manga"
                setUrlWithoutDomain(permalink)
                val rawCover = itemObj["cover"]?.jsonPrimitive?.content
                val cover = if (rawCover?.contains("poster-fallback") == true) "" else rawCover
                thumbnail_url = cover?.takeIf { it.isNotBlank() }?.replace(Regex("https?://(cdn\\.)?myanimelist\\.net"), "https://cdn.myanimelist.net")
            }
        } ?: emptyList()

        return MangasPage(mangas, false)
    }

    // ========================= Details =========================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.text-3xl, h1:not(.tmo-title)")?.text()?.trim()
                ?: document.selectFirst("meta[property=og:image:alt]")?.attr("content")
                ?: ""

            description = document.select("div[data-syn-full], div[data-syn-short], p.description, div.entry-content").text().trim()

            val authorEl = document.select("span.author, div.author, a[href*='/author/']")
            author = authorEl.text().trim().takeIf { it.isNotEmpty() }

            val genres = document.select("a[href*='/manga-genre/'], a[href*='/category/']")
            genre = genres.joinToString { it.text().trim() }.takeIf { it.isNotEmpty() }

            val coverImg = document.selectFirst("div.md\\:w-64 img, div.flex-shrink-0 > img[alt], img.mn-hero-thumb, img.cover, meta[property=og:image]")
                ?: document.selectFirst("img[src*='wp-content/uploads']")
            thumbnail_url = (coverImg?.attr("abs:src")?.takeIf { it.isNotEmpty() } ?: coverImg?.attr("content"))?.replace(Regex("https?://(cdn\\.)?myanimelist\\.net"), "https://cdn.myanimelist.net")

            val statusText = document.select("span.status, div.status, div.flex.flex-wrap.gap-2 span").text()
            status = parseStatus(statusText)
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("En emisión", ignoreCase = true) || status.contains("Publicándose", ignoreCase = true) -> SManga.ONGOING
        status.contains("Finalizado", ignoreCase = true) || status.contains("Completado", ignoreCase = true) -> SManga.COMPLETED
        status.contains("Pausado", ignoreCase = true) -> SManga.ON_HIATUS
        status.contains("Cancelado", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ========================= Chapters =========================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterLinks = document.select("a.chapter-card, a[href*='/manga-chapter/']")

        val chapters = chapterLinks.mapNotNull { element ->
            val href = element.attr("href")
            if (!href.contains("/manga-chapter/")) return@mapNotNull null

            // Extraer el texto completo del enlace (ej: "1 Capítulo 1 17/07/2026 menu_book")
            // o extraer explícitamente los spans:
            val spans = element.select("span")
            val nameText = if (spans.size >= 2) {
                // El primer span suele ser el número, el segundo el nombre "Capítulo 1"
                spans[1].text().trim()
            } else {
                element.text().trim()
            }

            val numVal = Regex("""capitulo-(\d+(\.\d+)?)""").find(href)?.groupValues?.get(1)?.toFloatOrNull()
                ?: Regex("""\d+(\.\d+)?""").find(nameText)?.value?.toFloatOrNull()
                ?: -1f

            val dateStr = element.select("span").lastOrNull { it.text().matches(Regex("""\d{2}/\d{2}/\d{4}""")) }?.text()?.trim()
                ?: element.selectFirst("span.date, small, span.text-muted")?.text()?.trim()

            val finalName = if (nameText.contains("Capítulo", ignoreCase = true)) {
                nameText
            } else {
                "Capítulo $numVal"
            }

            SChapter.create().apply {
                name = finalName.ifEmpty { "Capítulo" }
                chapter_number = numVal
                // Si la fecha está en formato DD/MM/YYYY, necesitamos usar DATE_FORMAT_2
                date_upload = if (dateStr != null && dateStr.contains("/")) {
                    DATE_FORMAT_2.tryParse(dateStr)
                } else {
                    DATE_FORMAT.tryParse(dateStr)
                }
                setUrlWithoutDomain(href)
            }
        }.distinctBy { it.url }

        // El HTML lista del más viejo (arriba) al más nuevo (abajo), así que invertimos el orden
        return chapters.reversed()
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        // Extraer URLs del script JS `const ALL_PAGES = [...]`
        val scripts = document.select("script").html()
        val pagesMatch = Regex("""ALL_PAGES\s*=\s*(\[[^\]]+\])""").find(scripts)

        if (pagesMatch != null) {
            try {
                val jsonStr = pagesMatch.groupValues[1].replace("""\/""", "/")
                val jsonArray = Json.parseToJsonElement(jsonStr).jsonArray
                jsonArray.forEachIndexed { index, element ->
                    val url = element.jsonPrimitive.content
                    if (url.isNotBlank()) {
                        pages.add(Page(index, "", url))
                    }
                }
            } catch (_: Exception) {}
        }

        if (pages.isEmpty()) {
            val images = document.select("main.mn-pages-strip img, div.reader-content img, div.entry-content img")
            images.forEachIndexed { index, img ->
                val imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
                if (imageUrl.isNotEmpty()) {
                    pages.add(Page(index, "", imageUrl))
                }
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        private val DATE_FORMAT_2 = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    }
}
