package eu.kanade.tachiyomi.extension.ko.nameetoon

import eu.kanade.tachiyomi.extension.ko.nameetoon.NameeToon.Companion.ALL_GENRES
import eu.kanade.tachiyomi.extension.ko.nameetoon.NameeToon.Companion.COMPLETED
import eu.kanade.tachiyomi.extension.ko.nameetoon.NameeToon.Companion.GENRE_SEPARATOR
import eu.kanade.tachiyomi.extension.ko.nameetoon.NameeToon.Companion.KOREA_ZONE
import eu.kanade.tachiyomi.extension.ko.nameetoon.NameeToon.Companion.ONGOING
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

// 야툰's own template, over the same series and episodes as the other fronts
internal class Yatoon(private val source: NameeToon) {

    // A list page starts after the last card of the page before it: the cursors already seen are kept page by page
    private val cursors = ConcurrentHashMap<String, MutableMap<Int, List<Pair<String, String>>>>()

    suspend fun getPopularManga(page: Int): MangasPage = getCards(page, listOf("fin" to "ongoing", "sort" to "popular"))

    // The search looks through ongoing and completed series alike
    suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return getCards(page, listOf("keyword" to query.trim(), "sort" to "latest"))
        val day = filters.firstInstanceOrNull<DayFilter>()?.takeIf { it.selected != null }
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected?.takeIf { it != ALL_GENRES }
        val params = buildList {
            add("fin" to if (filters.firstInstanceOrNull<StatusFilter>()?.selected == COMPLETED) "complete" else "ongoing")
            // As on the other fronts, a day leaves the genre out
            if (day != null) {
                add("day" to day.values[day.state])
            } else if (genre != null) {
                add("genre" to genre)
            }
            add("sort" to "latest")
        }
        return getCards(page, params)
    }

    private suspend fun getCards(page: Int, params: List<Pair<String, String>>): MangasPage {
        val pageCursors = cursors.getOrPut(params.toString()) { ConcurrentHashMap() }
        // Mihon asks for the pages in order; a page whose cursor isn't known yet is reached from the closest one before it
        var current = (page downTo 2).firstOrNull { it in pageCursors } ?: 1
        var cards = fetchCards(params + pageCursors[current].orEmpty())
        while (current < page && cards.size == PAGE_SIZE) {
            pageCursors[++current] = cards.last().cursor(params)
            cards = fetchCards(params + pageCursors[current].orEmpty())
        }
        if (current < page) return MangasPage(emptyList(), false)
        if (cards.size == PAGE_SIZE) pageCursors[page + 1] = cards.last().cursor(params)
        return MangasPage(cards.map { it.toSManga() }, cards.size == PAGE_SIZE)
    }

    private suspend fun fetchCards(params: List<Pair<String, String>>): List<Element> {
        val url = "${source.baseUrl}/ajax/webtoon-list.php".toHttpUrl().newBuilder().apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        return source.client.get(url, source.headers, Fetch.XHR).asJsoup().select("article.webtoon-card")
    }

    // The popular order goes on by views, the others by update time
    private fun Element.cursor(params: List<Pair<String, String>>) = listOf(
        "cursor_id" to attr("data-id"),
        if (("sort" to "popular") in params) "cursor_hit" to attr("data-hit") else "cursor_last" to attr("data-last"),
    )

    private fun Element.toSManga() = SManga.create().apply {
        url = attr("data-id")
        title = selectFirst(".webtoon-info h3")!!.text()
        thumbnail_url = selectFirst(".webtoon-thumb img")?.absUrl("src")
    }

    // A series link, or an episode one through its link back to the series
    suspend fun getSeriesId(url: HttpUrl): String? {
        val episodeId = when {
            url.pathSegments.firstOrNull() == "viewer" -> url.pathSegments.getOrNull(1)
            url.pathSegments.firstOrNull() == "viewer.php" -> url.queryParameter("id")
            else -> return url.queryParameter("episode_list")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        }
        if (episodeId.isNullOrEmpty() || !episodeId.all(Char::isDigit)) return null
        return source.client.get(url, source.headers, Fetch.DOCUMENT).asJsoup()
            .selectFirst("a.viewer-list-button")?.absUrl("href")?.toHttpUrl()?.queryParameter("episode_list")
    }

    fun getMangaUrl(manga: SManga): String = "${source.baseUrl}/?episode_list=${manga.url}"

    suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val url = "${source.baseUrl}/ajax/episode-list.php".toHttpUrl().newBuilder().addQueryParameter("id", manga.url).build()
        val document = source.client.get(url, source.headers, Fetch.XHR).asJsoup()
        return SMangaUpdate(
            manga = { if (fetchDetails) parseDetails(manga.url, document) else manga },
            chapters = { if (fetchChapters) getChapters(manga.url, document) else chapters },
        )
    }

    private fun parseDetails(id: String, document: Document): SManga {
        val info = document.select(".episode-meta-label").map { it.text() }
            .zip(document.select(".episode-meta-value").map { it.text() })
            .toMap()
        return SManga.create().apply {
            url = id
            title = document.selectFirst(".episode-webtoon-title")!!.text()
            thumbnail_url = document.selectFirst(".episode-cover img")?.absUrl("src")
            author = info["작가"]
            description = MarkdownDescription.Builder()
                .info(info["작품"])
                .description(document.selectFirst(".episode-intro"))
                .build()
            genre = info["장르"]?.split(GENRE_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.joinToString()
                ?.ifEmpty { null }
            status = when (document.selectFirst(".episode-status")?.text()) {
                ONGOING -> SManga.ONGOING
                COMPLETED -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // The series view holds the first 50 episodes, the rest come 50 at a time after the last one
    private suspend fun getChapters(id: String, document: Document): List<SChapter> {
        val chapters = document.select("a.episode-item").mapTo(mutableListOf()) { it.toSChapter() }
        var cursor = document.selectFirst("#episodeScrollSentinel[data-has-more=1]")?.attr("data-cursor-id")
        while (cursor != null) {
            val url = "${source.baseUrl}/ajax/episode-more.php".toHttpUrl().newBuilder()
                .addQueryParameter("id", id)
                .addQueryParameter("cursor_id", cursor)
                .build()
            val more = source.client.get(url, source.headers, Fetch.XHR).parseAs<MoreEpisodesDto>()
            Jsoup.parseBodyFragment(more.html, source.baseUrl).select("a.episode-item").mapTo(chapters) { it.toSChapter() }
            cursor = more.nextCursor
        }
        return chapters
    }

    private fun Element.toSChapter() = SChapter.create().apply {
        url = attr("data-episode-id")
        name = selectFirst(".episode-item-title")!!.text()
        date_upload = parseDate(selectFirst(".episode-item-date")?.text())
    }

    // Only relative dates there, down to the year for older episodes
    private fun parseDate(text: String?): Long {
        val (amount, unit) = text?.let { RELATIVE_DATE.matchEntire(it) }?.destructured ?: return 0L
        val now = ZonedDateTime.now(KOREA_ZONE)
        val date = when (unit) {
            "분" -> now.minusMinutes(amount.toLong())
            "시간" -> now.minusHours(amount.toLong())
            "일" -> now.minusDays(amount.toLong())
            "개월" -> now.minusMonths(amount.toLong())
            else -> now.minusYears(amount.toLong())
        }
        return date.toInstant().toEpochMilli()
    }

    fun getChapterUrl(chapter: SChapter): String = "${source.baseUrl}/viewer/${chapter.url}"

    suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = source.client.get(getChapterUrl(chapter), source.headers, Fetch.DOCUMENT).asJsoup()
        return document.select(".viewer-images img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
        private val RELATIVE_DATE = Regex("""(\d+)\s*(분|시간|일|개월|년) 전""")
    }
}
