package eu.kanade.tachiyomi.extension.en.pornhwadbreader

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class PornhwadbReader : KeiSource() {

    override val supportsRelatedMangas = true

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int) = browse(page) { addQueryParameter("sort", "read") }

    override suspend fun getLatestUpdates(page: Int) = browse(page) {}

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = browse(page) {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
    }

    private suspend fun browse(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply(params)
            .addQueryParameter("page", page.toString())
            .build()
        val document = fetchDocument(url.toString())
        return MangasPage(parseCards(document), document.selectFirst("nav.pagination a.pagination-step:contains(Next)") != null)
    }

    private fun parseCards(root: Element) = root.select("article.cover-card:has(h3.cover-title a)").map { card ->
        val link = card.selectFirst("h3.cover-title a")!!
        SManga.create().apply {
            url = link.absUrl("href").toHttpUrl().pathSegments[1]
            title = link.text()
            thumbnail_url = card.selectFirst("img")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "series" || segments[1].toIntOrNull() == null) return null
        return parseDetails(fetchDocument("$baseUrl/series/${segments[1]}"))
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = fetchDocument(getMangaUrl(manga))
        return SMangaUpdate(parseDetails(document), if (fetchChapters) parseChapters(document) else chapters)
    }

    private fun parseDetails(document: Document): SManga {
        val main = document.selectFirst("section.series-main")!!
        return SManga.create().apply {
            url = document.selectFirst("link[rel=canonical]")!!.absUrl("href").toHttpUrl().pathSegments[1]
            title = main.selectFirst("h1#series-title")!!.text()
            thumbnail_url = main.selectFirst(".series-cover img")?.absUrl("src")
            author = main.select("p.credits > span:containsOwn(Story) a").joinToString { it.text() }.ifEmpty { null }
            artist = main.select("p.credits > span:containsOwn(Art) a").joinToString { it.text() }.ifEmpty { null }
            genre = main.select("ul[aria-label=Genres] a").joinToString { it.text() }.ifEmpty { null }
            status = parseStatus(main.selectFirst("dt:containsOwn(Status) + dd")?.text())
            description = buildString {
                main.selectFirst(".synopsis > p")?.textOrNull()?.let(::append)
                val altTitles = main.select("dt:containsOwn(Also known as) ~ dd").map { it.text() }
                if (altTitles.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative titles: ", altTitles.joinToString())
                }
            }.ifEmpty { null }
        }
    }

    private fun parseStatus(status: String?): Int {
        val lowercase = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "ongoing" in lowercase -> SManga.ONGOING
            "completed" in lowercase -> SManga.COMPLETED
            "hiatus" in lowercase -> SManga.ON_HIATUS
            "cancel" in lowercase -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val similar = fetchDocument(getMangaUrl(manga)).selectFirst("section[aria-labelledby=similar-heading]")
        return similar?.let(::parseCards).orEmpty()
    }

    // Each edition (standard/uncensored) has its own paginated list, the switch links the ones not shown.
    // Some chapters have no number; they keep the position of the chapter above them in their edition.
    private suspend fun parseChapters(document: Document): List<SChapter> {
        val otherEditions = document.select(".edition-switch a:not([aria-current])").map { it.absUrl("href") }
        val editions = listOf(collectEdition(document)) + otherEditions.map { collectEdition(fetchDocument(it)) }
        return editions.flatMap { chapters ->
            var position = Float.MAX_VALUE
            chapters.map { chapter ->
                if (chapter.chapter_number >= 0) position = chapter.chapter_number
                position to chapter
            }
        }.sortedByDescending { it.first }.map { it.second }
    }

    private suspend fun collectEdition(firstPage: Document): List<SChapter> = buildList {
        var document = firstPage
        while (true) {
            document.select("ol.chapter-list > li:has(a[data-edition])").mapTo(this, ::parseChapter)
            val next = document.selectFirst(".chapter-results nav.pagination a.pagination-step:contains(Next)") ?: break
            document = fetchDocument(next.absUrl("href"))
        }
    }

    private fun parseChapter(element: Element): SChapter {
        val link = element.selectFirst("a[data-edition]")!!
        return SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.selectFirst(".chapter-title")!!.ownText()
            chapter_number = element.attr("data-number").toFloatOrNull() ?: -1f
            date_upload = Instant.tryParse(link.selectFirst("time")?.attr("datetime"))
            scanlator = link.attr("data-edition").replaceFirstChar { it.uppercase() }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter))
        .extractNextJs<ReaderChapter> { it is JsonObject && "edition" in it && it["pages"] is JsonArray }!!
        .toPages()

    override suspend fun fetchFilterData(): JsonElement {
        val document = fetchDocument("$baseUrl/browse")
        fun values(param: String) = document.select("a[href^=\"/browse?$param=\"]")
            .mapNotNull { it.absUrl("href").toHttpUrl().queryParameter(param) }
            .distinct()
        return FilterData(values("g"), values("year")).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val fetched = data?.parseAs<FilterData>()
        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                add(RatingFilter())
                if (fetched != null) add(GenreFilter(fetched.genres))
                add(UncensoredFilter())
                add(LengthFilter())
                add(UpdatedFilter())
                if (fetched != null) add(YearFilter(fetched.years))
                add(OrientationFilter())
            },
        )
    }

    private suspend fun fetchDocument(url: String) = client.get(url).asJsoup().apply { resolveStreamedSegments() }

    // React streams late Suspense content into hidden `S:n` divs and moves each one into its
    // `P:n` placeholder with JS, do the same so parts of a card aren't left elsewhere in the page
    private fun Document.resolveStreamedSegments() {
        select("template[id^=P:]").forEach { placeholder ->
            val segment = getElementById("S:" + placeholder.id().substringAfter(':')) ?: return@forEach
            segment.childNodes().toList().forEach(placeholder::before)
            placeholder.remove()
            segment.remove()
        }
    }
}
