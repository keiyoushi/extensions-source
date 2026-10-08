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

        // The default list keeps one upload per chapter, so list every group's uploads separately.
        val groups = document.select("select[name=source] option:not([value=''])").map { it.attr("value") }
        val chapterList = if (groups.isEmpty()) {
            fetchChapters(manga, null, document)
        } else {
            coroutineScope {
                groups.map { async { fetchChapters(manga, it) } }.awaitAll().flatten()
            }
        }

        // Groups are listed one after another, so interleave them by number and then date.
        return SMangaUpdate(details, chapterList.sortedWith(compareByDescending<SChapter> { it.chapter_number }.thenByDescending { it.date_upload }))
    }

    private suspend fun fetchChapters(manga: SManga, group: String?, firstPage: Document? = null): List<SChapter> {
        fun pageUrl(page: Int) = getMangaUrl(manga).toHttpUrl().newBuilder().apply {
            group?.let { addQueryParameter("source", it) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        val document = firstPage ?: client.get(pageUrl(1)).asDocument()
        val lastPage = document.select(".pagination-page a[href$=#chapters]").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1

        return parseChapters(document) + coroutineScope {
            (2..lastPage).map { page ->
                async { parseChapters(client.get(pageUrl(page)).asDocument()) }
            }.awaitAll().flatten()
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("li.ch-row").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("a.ch-link")!!
            url = link.absUrl("href").toHttpUrl().pathSegments[1]
            val number = link.selectFirst(".ch-num")!!
            val title = link.selectFirst(".ch-title")!!
            // A plain title is the full display name, e.g. "Chapter 48" or an unnumbered extra.
            name = if (title.hasClass("is-plain")) title.text() else listOfNotNull(number.textOrNull(), title.text()).joinToString(": ")
            number.ownText().toFloatOrNull()?.let { chapter_number = it }
            // Rows with a single upload use .ch-source, rows with several put the group in a dropdown trigger.
            scanlator = element.selectFirst(".ch-source, .ch-source-name")?.textOrNull()
            date_upload = Instant.tryParse(element.selectFirst("time.ch-time")?.attr("datetime"))
        }
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
}
