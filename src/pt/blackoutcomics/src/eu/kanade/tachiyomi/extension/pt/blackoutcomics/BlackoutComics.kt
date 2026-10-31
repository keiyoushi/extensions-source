package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class BlackoutComics : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = addCookie(
        listOf(
            "age_gate_consent" to """{"consentAt":1790812800000,"expiresAt":2145916800000}""",
            "_popprepop" to "1",
        ),
    )

    var searchCsrf: String? = null

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val doc = client.get("$baseUrl/ranking", Fetch.DOCUMENT).asJsoup().extractCsrf()
        val mangas = doc.select(".ranking-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val doc = client.get("$baseUrl/atualizados-recente?page=$page", Fetch.DOCUMENT).asJsoup().extractCsrf()
        val mangas = doc.select(".webtoon-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        val hasNext = doc.select(".pagerx__link[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNext)
    }

    // =============================== Search ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.count(String::isNotBlank) < 2) return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = if (query.isNotBlank()) {
            if (searchCsrf == null) {
                client.get(baseUrl, Fetch.DOCUMENT).asJsoup().extractCsrf()
            }
            val searchHeaders = headersBuilder()
                .set("x-csrf-token", searchCsrf.orEmpty())
                .set("x-requested-with", "XMLHttpRequest")
                .build()
            val body = SearchPayload(query).toJsonRequestBody()
            runCatching {
                client.post("$baseUrl/comics/search-preview", searchHeaders, Fetch.XHR, body)
            }.onFailure { searchCsrf = null }.getOrThrow()
        } else {
            val url = "$baseUrl/comics".toHttpUrl().newBuilder().apply {
                val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
                val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()

                if (!status.isNullOrEmpty()) addQueryParameter("status", status)
                if (!genre.isNullOrEmpty()) addQueryParameter("gen", genre)
            }.build()
            client.get(url, Fetch.DOCUMENT)
        }
        return parseSearch(response)
    }

    private fun parseSearch(response: Response): MangasPage = if (response.request.method == "POST") {
        val searchResponse = response.parseAs<SearchResponse>()
        val mangas = searchResponse.items.map { it.toSManga(baseUrl) }
        MangasPage(mangas, false)
    } else {
        val doc = response.asJsoup()
        val mangas = doc.select(".webtoon-grid a.webtoon-card").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                title = el.select(".card-title span").text()
                thumbnail_url = el.select(".card-thumb img").attr("abs:src")
            }
        }
        MangasPage(mangas, false)
    }

    // =========================== MangaUpdate ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            parseDetails(doc),
            parseChapters(doc),
        )
    }

    private fun parseDetails(doc: Document) = SManga.create().apply {
        setUrlWithoutDomain(doc.location())
        title = doc.select(".project-title").text()
        thumbnail_url = doc.select(".project-cover").attr("abs:src")
        author = doc.select(".quick-info-item:has(.fa-pen-nib) strong").text()
        artist = doc.select(".quick-info-item:has(.fa-palette) strong").text()
        description = doc.select(".project-description").text()
        genre = doc.select(".project-genres .genre-tag").joinToString { it.text() }

        val statusText = doc.select(".status-pill").text().lowercase()
        status = when {
            statusText.contains("lançamento") -> SManga.ONGOING
            statusText.contains("completo") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(doc: Document): List<SChapter> = doc.select("#tab-capitulos-list .normal_ep").map { el ->
        SChapter.create().apply {
            val linkElement = el.selectFirst("a[href]")
            val num = el.select(".num").text()

            if (linkElement != null) {
                setUrlWithoutDomain(linkElement.attr("abs:href"))
            } else {
                url = doc.location().toHttpUrl().encodedPath + "/ler/capitulo-$num"
            }

            var chapterName = "Capítulo $num"
            val title = el.select(".cell-title strong.line-3").text()
            if (title.isNotEmpty()) {
                chapterName += " - $title"
            }
            name = chapterName

            date_upload = dateFormat.tryParseDate(el.select(".cell-num .text-muted").text())
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val doc = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        for (script in doc.select("script:not([src])")) {
            val match = PAGE_LIST_REGEX.find(script.html()) ?: continue
            val urls = match.groupValues[1].parseAs<List<String>>()
            return urls.mapIndexed { i, url ->
                Page(i, imageUrl = if (url.startsWith("http")) url else "$baseUrl$url")
            }
        }
        if (doc.location() != chapterUrl) {
            error("Necessário fazer login ou vincule seu Discord em WebView")
        }
        return emptyList()
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .removeHeader("Referer")
        .build()

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        StatusFilter(),
        GenreFilter(),
    )

    // ============================== Utilities =============================
    fun Document.extractCsrf() = apply {
        searchCsrf = selectFirst("meta[name=csrf-token]")?.attr("content")
    }
    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.ROOT)
        private val PAGE_LIST_REGEX = Regex("""S\s*=\s*(\[[\s\S]*?])""")
    }
}
