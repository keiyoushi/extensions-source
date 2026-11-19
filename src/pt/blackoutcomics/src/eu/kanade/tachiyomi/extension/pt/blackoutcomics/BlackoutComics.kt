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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    private var searchCsrf: String? = null

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/ranking", Fetch.DOCUMENT).asJsoup().extractCsrf()
        val mangas = document.select("#rankingContent a[href*=/comics/]").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/atualizados-recente".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup().extractCsrf()
        val mangas = document.select(".webtoon-grid a.webtoon-card").map { it.toSManga() }
        val hasNextPage = document.selectFirst(".pagerx__link[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.encodedPathSegments.filter(String::isNotBlank)
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 2 || segments[0] != "comics") return null

        val manga = SManga.create().apply { this.url = "/comics/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            if (searchCsrf == null) {
                client.get(baseUrl, Fetch.DOCUMENT).asJsoup().extractCsrf()
            }
            val searchHeaders = headersBuilder()
                .set("x-csrf-token", searchCsrf.orEmpty())
                .set("x-requested-with", "XMLHttpRequest")
                .build()
            val body = SearchPayload(query).toJsonRequestBody()
            val response = runCatching {
                client.post("$baseUrl/comics/search-preview", searchHeaders, Fetch.XHR, body)
            }.onFailure { searchCsrf = null }.getOrThrow()
            val mangas = response.parseAs<SearchResponse>().items.map { it.toSManga(baseUrl) }
            return MangasPage(mangas, false)
        }

        val url = "$baseUrl/comics".toHttpUrl().newBuilder().apply {
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.selected?.let { addQueryParameter("gen", it) }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".webtoon-grid a.webtoon-card").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            parseDetails(document),
            parseChapters(document),
        )
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst(".project-title")!!.text()
        thumbnail_url = document.selectFirst(".project-cover")?.attrOrNull("abs:src")
        author = document.selectFirst(".quick-info-item:has(.fa-pen-nib) strong")?.textOrNull()
        artist = document.selectFirst(".quick-info-item:has(.fa-palette) strong")?.textOrNull()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".project-description"))
            .build()
        genre = document.select(".project-genres .genre-tag")
            .takeIf { it.isNotEmpty() }
            ?.joinToString { it.text() }
        val statusText = document.selectFirst(".status-pill")?.text()?.lowercase().orEmpty()
        status = when {
            "lançamento" in statusText -> SManga.ONGOING
            "completo" in statusText -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("#tab-capitulos-list .normal_ep").map { element ->
        SChapter.create().apply {
            val number = element.selectFirst(".num")!!.text()
            val link = element.selectFirst("a[href]")
            if (link != null) {
                setUrlWithoutDomain(link.absUrl("href"))
            } else {
                url = document.location().toHttpUrl().newBuilder()
                    .addPathSegment("ler")
                    .addPathSegment("capitulo-$number")
                    .build()
                    .encodedPath
            }
            val chapterTitle = element.selectFirst(".cell-title strong.line-3")?.textOrNull()
            name = listOfNotNull("Capítulo $number", chapterTitle).joinToString(" - ")
            date_upload = DATE_FORMAT.tryParseDate(element.selectFirst(".cell-num .text-muted")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        for (script in document.select("script:not([src])")) {
            val match = PAGE_LIST_REGEX.find(script.html()) ?: continue
            val urls = match.groupValues[1].parseAs<List<String>>()
            return urls.mapIndexed { index, url ->
                Page(index, imageUrl = baseUrl.toHttpUrl().resolve(url)!!.toString())
            }
        }
        if (document.location() != chapterUrl) {
            error("Log in and link Discord in WebView")
        }
        return emptyList()
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .removeHeader("Referer")
        .build()

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/comics", Fetch.DOCUMENT).asJsoup()
        return document.select("li.generos-blackout a[href*='gen=']")
            .map { it.absUrl("href").toHttpUrl().queryParameter("gen")!! }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(StatusFilter())
            data?.parseAs<List<String>>()?.let { add(GenreFilter(it)) }
        },
    )

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst("h2, .card-title")!!.text()
        thumbnail_url = selectFirst("img")?.attrOrNull("abs:src")
    }

    private fun Document.extractCsrf() = apply {
        searchCsrf = selectFirst("meta[name=csrf-token]")?.attr("content")
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.ROOT)
        private val PAGE_LIST_REGEX = Regex("""S\s*=\s*(\[[\s\S]*?])""")
    }
}
