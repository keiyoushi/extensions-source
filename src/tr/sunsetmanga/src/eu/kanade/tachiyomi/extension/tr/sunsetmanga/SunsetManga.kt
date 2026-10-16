package eu.kanade.tachiyomi.extension.tr.sunsetmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class SunsetManga : KeiSource() {
    private val ajaxUrl get() = "$baseUrl/wp-admin/admin-ajax.php"
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("tr"))
    private val nonceRegex = Regex("""sunsetGlobalData\s*=\s*\{[^}]*nonce:\s*"([a-z0-9]+)"""")
    private var nonce: String? = null

    private suspend fun getNonce(): String {
        nonce?.let { return it }
        val scriptData = client.get(baseUrl).asJsoup()
            .select("script")
            .firstOrNull { it.data().contains("sunsetGlobalData") }
            ?.data().orEmpty()
        val found = nonceRegex.find(scriptData)?.groupValues?.get(1)
            ?: throw Exception("Nonce not found")
        nonce = found
        return found
    }

    private inline fun <reified T> Response.parseJson(): T = parseAs { it.removePrefix("\uFEFF") }

    override suspend fun getPopularManga(page: Int): MangasPage = getCatalog(page, "rating")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getCatalog(page, "latest")

    private suspend fun getCatalog(page: Int, sort: String): MangasPage {
        val filters = buildJsonObject {
            put("search", "")
            put("genres", buildJsonArray {})
            put("type", "")
            put("status", "")
            put("sort", sort)
            put("page", page)
        }.toString()
        val body = FormBody.Builder()
            .add("action", "load_manga_catalog")
            .add("filters", filters)
            .add("nonce", getNonce())
            .build()
        val response = client.post(ajaxUrl, body = body).parseJson<CatalogResponse>()
        val data = response.data?.takeIf { response.success } ?: return MangasPage(emptyList(), false)
        val document = Jsoup.parseBodyFragment(data.html)
        val mangas = document.select("div.group.relative > a[href*=/manga/]").mapNotNull(::parseCard)
        return MangasPage(mangas, data.hasMore)
    }

    private fun parseCard(element: Element): SManga? {
        val title = element.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() } ?: return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(element.attr("abs:href"))
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getCatalog(page, "latest")
        if (page > 1) return MangasPage(emptyList(), false)
        val body = FormBody.Builder()
            .add("action", "manga_search")
            .add("search_term", query)
            .add("nonce", getNonce())
            .build()
        val results = client.post(ajaxUrl, body = body).parseJson<List<SearchResult>>()
        val mangas = results.mapNotNull { result ->
            val title = result.title.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(result.url)
                thumbnail_url = result.thumbnail
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseDetails(document, manga), parseChapters(document))
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        url = manga.url
        title = document.selectFirst("h1.manga-title")?.text() ?: manga.title
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        description = document.selectFirst("meta[name=description]")?.attr("content")
        genre = document.select("a[role=listitem][href*=/tur/]").joinToString { it.text() }
        status = document.selectFirst("a[aria-label*=durumundaki]")?.attr("href").parseStatus()
        initialized = true
    }

    private fun String?.parseStatus(): Int = when (this?.toHttpUrlOrNull()?.queryParameter("status")) {
        "ongoing" -> SManga.ONGOING
        "finished" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseChapters(document: Document): List<SChapter> {
        return document.select("#chapters-container .chapter-item").mapNotNull { element ->
            val anchor = element.selectFirst("a[href]") ?: return@mapNotNull null
            val name = element.selectFirst(".chapter-title")?.text()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SChapter.create().apply {
                this.name = name
                setUrlWithoutDomain(anchor.attr("abs:href"))
                date_upload = dateFormat.tryParseDate(element.selectFirst(".chapter-date span")?.text())
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = document.select(".protected-image-container[data-token]").mapIndexedNotNull { index, element ->
            val token = element.attr("data-token").takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            Page(index, imageUrl = token)
        }
        if (pages.isEmpty() && document.selectFirst(":containsOwn(Erken Erişim)") != null) {
            throw Exception("This is a VIP chapter (Erken Erişim).")
        }
        return pages
    }

    override fun imageRequest(page: Page): Request {
        val body = FormBody.Builder()
            .add("action", "get_protected_image")
            .add("token", page.imageUrl!!)
            .build()
        return Request.Builder()
            .url(ajaxUrl)
            .post(body)
            .headers(headers)
            .build()
    }
}
