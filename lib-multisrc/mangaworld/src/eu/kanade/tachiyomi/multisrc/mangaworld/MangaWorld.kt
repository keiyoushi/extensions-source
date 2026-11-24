package eu.kanade.tachiyomi.multisrc.mangaworld

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
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

    override suspend fun getPopularManga(page: Int) = parseMangasPage(client.get("$baseUrl/archive?sort=most_read&page=$page", Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int) = parseMangasPage(client.get("$baseUrl/?page=$page", Fetch.DOCUMENT).asJsoup())

    protected open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst(".card-cover img")?.attr("abs:src")
        element.selectFirst(".card-title a")!!.let {
            setUrlWithoutDomain(it.attr("abs:href").removeSuffix("/"))
            title = it.attr("title").ifBlank { it.text() }
        }
    }

    protected open fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".archive-grid .archive-card, article.card").map { searchMangaFromElement(it) }
        val hasNextPage = document.selectFirst(".pager a[rel=next]") != null
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
        return parseMangasPage(client.get(url.build(), Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga" || url.pathSegments.size < 3) return null

        val mangaUrl = url.pathSegments.take(3).joinToString("/", prefix = "/")
        val document = client.get(baseUrl + mangaUrl, Fetch.DOCUMENT).asJsoup()
        return mangaDetailsParse(document).apply { this.url = mangaUrl }
    }

    // details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = mangaDetailsParse(document)
        return SMangaUpdate(details, chapterListParse(document))
    }

    protected open fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst(".manga-info")
            ?: throw Exception("Page not found")

        return SManga.create().apply {
            title = infoElement.selectFirst("h1.manga-title")?.text() ?: ""
            author = infoElement.selectFirst(".manga-facts a[href*='author=']")?.text()
            artist = infoElement.selectFirst(".manga-facts a[href*='artist=']")?.text() ?: ""
            thumbnail_url = document.selectFirst(".manga-cover img")?.attr("abs:src")

            description = buildString {
                infoElement.selectFirst("a[href*='year=']")?.let {
                    append("Anno di uscita: ${it.text()}")
                    append("\n\n")
                }

                append(document.select(".manga-plot").text())

                val otherTitle = infoElement.selectFirst(".manga-alt")?.text()
                if (!otherTitle.isNullOrEmpty() && otherTitle.contains("Titoli alternativi")) {
                    append("\n\n").append(otherTitle)
                }
            }

            genre = listOf("a[href*='type=']", ".hero-chips a").flatMap {
                infoElement.select(it)
            }.joinToString { it.text() }

            val statusText = infoElement.selectFirst(".manga-facts a[href*='status=']")?.text()
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

    protected open fun chapterListParse(document: Document): List<SChapter> = document.select(".volume-list .ch-row").map { element ->
        SChapter.create().apply {
            val urlElement = element.selectFirst("a.ch") ?: throw Exception("Url not found")
            setUrlWithoutDomain(fixChapterUrl(urlElement.attr("abs:href")))
            name = element.selectFirst(".ch-name")?.text() ?: ""
            date_upload = parseChapterDate(element.select(".ch-date").last()?.text())
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
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("img.reader-page").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("abs:src"))
        }
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
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
