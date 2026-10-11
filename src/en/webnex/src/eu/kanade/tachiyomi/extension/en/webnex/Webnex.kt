package eu.kanade.tachiyomi.extension.en.webnex

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class Webnex : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("coverage")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("updated")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asDocument()
        val mangas = document.select("li.card-item a.card-link").map { element ->
            SManga.create().apply {
                this.url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst(".card-title")!!.text()
                thumbnail_url = element.selectFirst("img.cover-img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return getMangaUpdate(
            manga = SManga.create().apply { this.url = slug },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asDocument()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.series-title")!!.text()
            thumbnail_url = document.selectFirst(".series-cover img")?.absUrl("src")
            author = document.select(".series-credits a[href*=author=]").joinToString { it.text() }.ifEmpty { null }
            artist = document.select(".series-credits a[href*=artist=]").joinToString { it.text() }.ifEmpty { null }
            genre = document.select(".series-facts a[href*=kind=], .tag-cloud a.chip").joinToString { it.text() }.ifEmpty { null }
            status = when (document.selectFirst(".fact-status")?.text()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            description = buildString {
                append(document.select(".series-desc p").joinToString("\n\n") { it.toMarkdown().trim() })
                val altTitles = document.select("details.series-more li").map { it.text() }
                if (altTitles.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative titles:\n")
                    append(altTitles.joinToString("\n") { "• $it" })
                }
            }.ifEmpty { null }
        }
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val lastPage = document.select(".pagination-page a[href$=#chapters]").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val otherPages = coroutineScope {
            (2..lastPage).map { page ->
                async { client.get(mangaUrl.newBuilder().addQueryParameter("page", page.toString()).build()).asDocument() }
            }.awaitAll()
        }

        // Dropdown uploads only have a relative time, so keep the date estimated when they were first seen.
        val knownDates = chapters.associate { it.url to it.date_upload }
        return SMangaUpdate(details, (listOf(document) + otherPages).flatMap { parseChapters(it, knownDates) })
    }

    // Each row is one chapter: the upload the site picked, plus the other groups' uploads in its dropdown.
    private fun parseChapters(document: Document, knownDates: Map<String, Long>): List<SChapter> = document.select("li.ch-row").flatMap { element ->
        val link = element.selectFirst("a.ch-link")!!
        val number = link.selectFirst(".ch-num")!!
        val title = link.selectFirst(".ch-title")!!
        // A plain title is the full display name, e.g. "Chapter 48" or an unnumbered extra.
        val chapterName = if (title.hasClass("is-plain")) title.text() else listOfNotNull(number.textOrNull(), title.text()).joinToString(": ")

        val picked = SChapter.create().apply {
            url = link.absUrl("href").toHttpUrl().pathSegments[1]
            name = chapterName
            // Rows with a single upload use .ch-source, rows with several put the group in a dropdown trigger.
            scanlator = element.selectFirst(".ch-source, .ch-source-name")?.textOrNull()
            date_upload = Instant.tryParse(element.selectFirst("time.ch-time")?.attr("datetime"))
        }
        val others = element.select(".ch-sources-menu a.ch-sources-item").map { item ->
            SChapter.create().apply {
                url = item.absUrl("href").toHttpUrl().pathSegments[1]
                name = chapterName
                scanlator = item.selectFirst(".ch-sources-name")?.textOrNull()
                date_upload = knownDates[url]?.takeIf { it != 0L } ?: item.selectFirst(".ch-sources-time")?.text().parseTimeAgo()
            }
        }
        listOf(picked) + others
    }

    // Inverts the site's timeAgo(): "4w ago" means 4 weeks up to the 30 days where "1mo" takes over, so take the middle.
    private fun String?.parseTimeAgo(): Long {
        if (this == "just now") return System.currentTimeMillis()
        val match = TIME_AGO_REGEX.matchEntire(this ?: return 0L) ?: return 0L
        val index = TIME_AGO_UNITS.indexOfFirst { it.first == match.groupValues[2] }
        val unit = TIME_AGO_UNITS[index].second
        val count = match.groupValues[1].toInt()
        val upper = minOf(unit * (count + 1), TIME_AGO_UNITS.getOrNull(index - 1)?.second ?: Duration.INFINITE)
        return System.currentTimeMillis() - ((unit * count + upper) / 2).inWholeMilliseconds
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    // Only the first few images are server-rendered; the full list lives in the reader's RSC props.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = client.get(getChapterUrl(chapter)).extractNextJs<ReaderDto> {
            it is JsonObject && "pages" in it && "chapter" in it
        }!!

        // Group uploads are relative /api/image/ paths, the site's own uploads are absolute CDN urls.
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        return reader.pages.mapIndexed { index, url ->
            Page(index, imageUrl = chapterUrl.resolve(url)!!.toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        KindFilter(),
        StatusFilter(),
        DemographicFilter(),
        LanguageFilter(),
        RatingFilter(),
        ChaptersFilter(),
        MatchFilter(),
        GenreFilter(),
        FormatFilter(),
        ThemeFilter(),
    )

    // Next.js streams late parts of the page as hidden chunks that client JS moves into <template> placeholders.
    private fun Response.asDocument(): Document = asJsoup().apply {
        select("div[hidden][id^=S:]").forEach { chunk ->
            val placeholder = getElementById("P:${chunk.id().removePrefix("S:")}") ?: return@forEach
            placeholder.parent()!!.insertChildren(placeholder.siblingIndex(), chunk.childNodes())
            placeholder.remove()
            chunk.remove()
        }
    }

    // Keep line breaks and links, descriptions render as Markdown.
    private fun Element.toMarkdown(): String = buildString {
        childNodes().forEach { node ->
            when (node) {
                is TextNode -> append(node.text())
                is Element -> when (node.normalName()) {
                    "br" -> append('\n')
                    "a" -> append("[${node.text()}](${node.absUrl("href")})")
                    "strong", "b" -> append("**${node.toMarkdown()}**")
                    else -> append(node.toMarkdown())
                }
            }
        }
    }

    companion object {
        private val TIME_AGO_REGEX = Regex("""(\d+)(mo|y|w|d|h|m) ago""")

        // Largest first, as the site's timeAgo() checks them.
        private val TIME_AGO_UNITS = listOf("y" to 365.days, "mo" to 30.days, "w" to 7.days, "d" to 1.days, "h" to 1.hours, "m" to 1.minutes)
    }
}
