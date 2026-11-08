package eu.kanade.tachiyomi.extension.th.dongmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.int
import keiyoushi.utils.long
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.Collator
import java.util.Locale

@Source
abstract class DongManga : KeiSource() {

    private val xhrHeaders get() = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = fetchSeries().sortedByDescending { it.views }.toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchSeries().sortedByDescending { it.lastThreadId }.toMangasPage()

    // Like the site's own catalogue page, which filters the full index in the browser
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val terms = query.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        val status = filters.firstInstanceOrNull<StatusFilter>()?.state ?: 0
        val sort = filters.firstInstanceOrNull<SortFilter>()?.state ?: 0
        val series = fetchSeries().filter { series ->
            (category == null || series.category == category) &&
                (status == 0 || series.isEnded == (status == 2)) &&
                terms.all { it in series.searchText }
        }
        val sorted = when (sort) {
            1 -> series.sortedByDescending { it.lastThreadId }
            2 -> series.sortedWith(compareByDescending<Series> { it.rating }.thenByDescending { it.votes }.thenByDescending { it.views })
            3 -> series.sortedWith(compareBy(Collator.getInstance(Locale.forLanguageTag("th"))) { it.name })
            else -> series.sortedByDescending { it.views }
        }
        return sorted.toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val fid = FORUM_PATH.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return null
        return client.get("$baseUrl/forum-$fid-1.html", Fetch.DOCUMENT).asJsoup().toSManga(fid)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/forum-${manga.url}-1.html"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga(manga.url), document.toSChapters())
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/thread-${chapter.url}-1-1.html"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        // Locked chapters only show a short preview and have no page token
        val token = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("#dz8pages[data-pg]")?.attr("data-pg") ?: return emptyList()
        val body = FormBody.Builder().add("pg", token).build()
        return client.post("$baseUrl/plugin.php?id=dong_wallet:pages", xhrHeaders, Fetch.XHR.from(chapterUrl), body)
            .parseAs<PagesDto>().img
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override suspend fun fetchFilterData(): JsonElement = fetchSeries().mapNotNull { it.category }.distinct().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            data?.parseAs<List<String>>()?.let(::CategoryFilter),
            StatusFilter(),
            SortFilter(),
        ),
    )

    // Every page carries the index of all series, used by the site for its instant search
    private suspend fun fetchSeries(): List<Series> = client.get("$baseUrl/forum.php?mod=guide", Fetch.DOCUMENT).asJsoup()
        .selectFirst("script#dz8idx")!!.data().parseAs()

    private fun List<Series>.toMangasPage() = MangasPage(map { it.toSManga() }, false)

    private fun Document.toSManga(fid: String) = SManga.create().apply {
        val header = selectFirst("section.dz8-sh")!!
        url = fid
        title = header.selectFirst("h1")!!.text()
        thumbnail_url = header.selectFirst(".dz8-sh-cov img")?.absUrl("src")
        genre = header.selectFirst(".dz8-shchip-genre")?.text()?.substringAfter(' ')
        status = when (header.selectFirst(".dz8-sh-tags .dz8-stamp")?.text()) {
            "กำลังออก" -> SManga.ONGOING
            "จบแล้ว" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val altTitles = header.selectFirst(".dz8-sh-alt")?.text()?.substringAfter(": ")
            ?.split(ALT_TITLE_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() && it != title }
        description = MarkdownDescription.Builder()
            .description(header.selectFirst("#dz8desc"))
            .altTitles(altTitles.orEmpty())
            .build()
    }

    private fun Document.toSChapters(): List<SChapter> {
        val translator = selectFirst(".dz8-sh-chips a[href*=uid=] b")?.text()
        // Each entry is [number, thread id, title, price in gems, ?, timestamp, views, ?]
        return selectFirst("script#dz8chidx")!!.data().parseAs<List<JsonArray>>()
            .filter { it[3].int == 0 } // Paid chapters can't be read without an account
            .map { entry ->
                SChapter.create().apply {
                    url = entry[1].long.toString()
                    name = entry[2].string
                    chapter_number = entry[0].string.toFloatOrNull() ?: -1f
                    date_upload = entry[5].long * 1000
                    scanlator = translator
                }
            }
    }

    companion object {
        private val FORUM_PATH = Regex("""/forum-(\d+)-\d+\.html""")
        private val WHITESPACE = Regex("""\s+""")
        private val ALT_TITLE_SEPARATOR = Char(0xFE31)
    }
}
