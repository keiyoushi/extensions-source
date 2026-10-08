package eu.kanade.tachiyomi.multisrc.mangaworld

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

private val chapterNumberRegex = Regex("""(?i)capitolo\s([0-9]+(?:\.[0-9]+)?)""")
private val dateFormat = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("d MMMM yyyy")
    .toFormatter(Locale.ITALY)

abstract class MangaWorld : KeiSource() {

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(CookieRedirectInterceptor(network.client))

    override suspend fun getPopularManga(page: Int) = parseMangasPage(client.get("$baseUrl/archive?sort=most_read&page=$page").asJsoup())

    override suspend fun getLatestUpdates(page: Int) = parseMangasPage(client.get("$baseUrl/?page=$page").asJsoup())

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("a.thumb img")?.attr("abs:src")
        element.selectFirst("a")?.let {
            setUrlWithoutDomain(it.attr("abs:href").removeSuffix("/"))
            title = it.attr("title")
        }
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select("div.comics-grid .entry").map { searchMangaFromElement(it) }
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/archive".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("genre", it.id)
                    }
                }
                is StatusList -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("status", it.id)
                    }
                }
                is MTypeList -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("type", it.id)
                    }
                }
                is SortBy -> url.addQueryParameter("sort", filter.toUriPart())
                is TextField -> {
                    if (filter.state.isNotEmpty()) {
                        url.addQueryParameter(filter.key, filter.state)
                    }
                }
                else -> {}
            }
        }
        return parseMangasPage(client.get(url.build()).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga" || url.pathSegments.size < 3) return null

        val mangaUrl = url.pathSegments.take(3).joinToString("/", prefix = "/")
        val document = client.get(baseUrl + mangaUrl).asJsoup()
        return mangaDetailsParse(document).apply {
            this.url = mangaUrl
            title = document.selectFirst("div.comic-info h1")!!.text()
        }
    }

    // details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = mangaDetailsParse(document).apply { title = manga.title }
        return SMangaUpdate(details, chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("div.comic-info")
            ?: throw Exception("Page not found")

        return SManga.create().apply {
            author = infoElement.selectFirst("a[href*=/archive?author=]")?.text()
            artist = infoElement.selectFirst("a[href*=/archive?artist=]")?.text() ?: ""
            thumbnail_url = infoElement.selectFirst(".thumb > img")?.attr("abs:src")

            description = buildString {
                append(document.select("div#noidungm").text())
                val otherTitle = document.selectFirst("div.meta-data > div")?.text()
                if (!otherTitle.isNullOrEmpty() && otherTitle.contains("Titoli alternativi")) {
                    append("\n\n").append(otherTitle)
                }
            }

            genre = infoElement.select("div.meta-data a.badge").joinToString { it.text() }

            val statusText = infoElement.selectFirst("a[href*=/archive?status=]")?.text()
            status = parseStatus(statusText)
        }
    }

    protected fun parseStatus(status: String?) = when (status?.lowercase()) {
        "in corso" -> SManga.ONGOING
        "finito" -> SManga.COMPLETED
        "in pausa" -> SManga.ON_HIATUS
        "cancellato" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select(".chapters-wrapper .chapter").map { element ->
        SChapter.create().apply {
            val urlElement = element.selectFirst("a.chap") ?: throw Exception("Url not found")
            setUrlWithoutDomain(fixChapterUrl(urlElement.attr("abs:href")))
            name = element.selectFirst("span.d-inline-block")?.text() ?: ""
            date_upload = parseChapterDate(element.select(".chap-date").last()?.text())
            parseChapterNumber(name)?.let { chapter_number = it }
        }
    }

    protected fun fixChapterUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        val params = url.substringAfter("?", "")
        return when {
            params.contains("style=list") -> url
            params.contains("style=pages") -> url.replace("style=pages", "style=list")
            params.isEmpty() -> "$url?style=list"
            else -> "$url&style=list"
        }
    }

    protected fun parseChapterDate(string: String?): Long = dateFormat.tryParseDate(string)

    protected fun parseChapterNumber(name: String): Float? = chapterNumberRegex.find(name)?.let { it.groups[1]?.value?.toFloatOrNull() }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div#page img.page-image").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("abs:src"))
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
        .build()

    override fun getFilterList(data: JsonElement?) = FilterList(
        TextField("Anno di uscita", "year"),
        SortBy(),
        StatusList(STATUSES),
        GenreList(GENRES),
        MTypeList(MTYPES),
    )
}
