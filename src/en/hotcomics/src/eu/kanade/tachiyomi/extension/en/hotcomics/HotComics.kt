package eu.kanade.tachiyomi.extension.en.hotcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HotComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addCookie("hc_vfs" to "Y")

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/en").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/en/new").asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addEncodedPathSegments("en/search")
                addQueryParameter("keyword", query.trim())
            } else {
                val filter = filters.firstInstance<BrowseFilter>()
                addEncodedPathSegments(filter.selected)
                addQueryParameter("page", page.toString())
            }
        }.build()

        return parseMangaList(client.get(url).asJsoup())
    }

    abstract class SelectFilter(
        name: String,
        private val options: List<Pair<String, String>>,
    ) : Filter.Select<String>(
        name,
        options.map { it.first }.toTypedArray(),
    ) {
        val selected get() = options[state].second
    }

    private val browseList = listOf(
        Pair("Home", "en"),
        Pair("Weekly", "en/weekly"),
        Pair("New", "en/new"),
        Pair("Genre: All", "en/genres"),
        Pair("Genre: Sports", "en/genres/Sports"),
        Pair("Genre: Historical", "en/genres/Historical"),
        Pair("Genre: Drama", "en/genres/Drama"),
        Pair("Genre: BL", "en/genres/BL"),
        Pair("Genre: Thriller", "en/genres/Thriller"),
        Pair("Genre: School life", "en/genres/School_life"),
        Pair("Genre: Comedy", "en/genres/Comedy"),
        Pair("Genre: GL", "en/genres/GL"),
        Pair("Genre: Action", "en/genres/Action"),
        Pair("Genre: Sci-fi", "en/genres/Sci-fi"),
        Pair("Genre: Horror", "en/genres/Horror"),
        Pair("Genre: Fantasy", "en/genres/Fantasy"),
        Pair("Genre: Romance", "en/genres/Romance"),
    )

    class BrowseFilter(browseList: List<Pair<String, String>>) : SelectFilter("Browse", browseList)

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Doesn't work with Text search"),
        Filter.Separator(),
        BrowseFilter(browseList),
    )

    private fun parseMangaList(document: Document): MangasPage {
        val entries = document.select("li[itemtype*=ComicSeries]:not(.no-comic) > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("div.visual img")?.imgAttr()
                title = element.selectFirst("div.main-text > h4.title")!!.text()
            }
        }.distinctBy { it.url }
        val hasNextPage = document.selectFirst("div.pagination a.vnext:not(.disabled)") != null

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            mangaDetailsParse(manga, document),
            chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(manga: SManga, document: Document) = manga.apply {
        title = document.selectFirst("h2.episode-title")!!.text()
        with(document.selectFirst("p.type_box")!!) {
            author = selectFirst("span.writer")?.text()
                ?.substringAfter("ⓒ")?.trim()
            genre = selectFirst("span.type")?.text()
                ?.split("/")?.joinToString { it.trim() }
            status = when (selectFirst("span.date")?.text()) {
                "End", "Ende" -> SManga.COMPLETED
                null -> SManga.UNKNOWN
                else -> SManga.ONGOING
            }
        }
        description = buildString {
            document.selectFirst("div.episode-contents header")
                ?.text()?.let {
                    append(it)
                    append("\n\n")
                }
            document.selectFirst("div.title_content > h2:not(.episode-title)")
                ?.text()?.let { append(it) }
        }.trim()
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select("#tab-chapter a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("onclick").substringAfter("popupLogin('").substringBefore("'"))
            name = element.selectFirst(".cell-num")!!.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".cell-time")?.text())
        }
    }.reversed()

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup().select("#viewer-img img").mapIndexed { idx, img ->
        Page(idx, imageUrl = img.imgAttr())
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
