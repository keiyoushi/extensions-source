package eu.kanade.tachiyomi.extension.all.manga18me

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
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manga18Me : KeiSource() {
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/manga/$page?orderby=trending").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/manga/$page?orderby=latest").asJsoup())

    private fun parseMangasPage(document: Document): MangasPage {
        val entries = document.select("div.page-item-detail")
        val hasNextPage = document.selectFirst(".next") != null
        if (lang == "en") {
            val searchText = document.selectFirst("div.section-heading h1")?.text().orEmpty()
            val raw = document.selectFirst("div.canonical")?.attr("href").orEmpty()
            return MangasPage(
                entries.filter { element ->
                    val href = element.selectFirst("div.item-thumb.wleft a")?.attr("href").orEmpty()
                    searchText.lowercase().contains("raw") || raw.contains("raw") || !href.contains("raw")
                }.map(::parseMangaFromElement),
                hasNextPage,
            )
        }
        return MangasPage(entries.map(::parseMangaFromElement), hasNextPage)
    }

    private fun parseMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("a")?.absUrl("href")?.let { setUrlWithoutDomain(it) }
        element.selectFirst("div.item-thumb.wleft img")?.attr("alt")?.let { title = it }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isEmpty()) {
                var completed = false
                var raw = false
                var genre = ""
                filters.forEach {
                    when (it) {
                        is GenreFilter -> genre = it.getValue()
                        is CompletedFilter -> completed = it.state
                        is RawFilter -> raw = it.state
                        is SortFilter -> addQueryParameter("orderby", it.getValue())
                        else -> {}
                    }
                }
                if (raw) {
                    addPathSegment("raw")
                } else if (completed) {
                    addPathSegment("completed")
                } else {
                    if (genre != "manga") addPathSegment("genre")
                    addPathSegment(genre)
                }
                addPathSegment(page.toString())
            } else {
                addPathSegment("search")
                addQueryParameter("q", query)
                addQueryParameter("page", page.toString())
            }
        }.build()
        return parseMangasPage(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val document = client.get("$baseUrl/manga/$slug").asJsoup()
        return parseMangaDetails(document).apply {
            this.url = "/manga/$slug"
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            parseMangaDetails(document),
            document.select("ul.row-content-chapter.wleft .a-h.wleft").map(::parseChapterFromElement),
        )
    }

    private fun parseMangaDetails(document: Document): SManga {
        val info = document.selectFirst("div.post_content")
        return SManga.create().apply {
            title = document.select("div.post-title.wleft > h1").text()
            description = buildString {
                document.selectFirst("div.ss-manga")
                    ?.wholeText()
                    ?.takeIf { it != "N/A" }
                    ?.takeIf { it.isNotEmpty() }
                    ?.also {
                        append(it)
                        append("\n")
                    }
                info?.selectFirst("div.post-content_item.wleft:contains(Alternative) div.summary-content")
                    ?.text()
                    ?.takeIf { it != "Updating" }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        append("Alternative Names:\n")
                        it.split("/", ";").forEach { alt ->
                            append("- ", alt.trim())
                            append("\n")
                        }
                    }
            }
            val statusElement = info?.selectFirst("div.post-content_item.wleft:contains(Status) div.summary-content")
            status = when (statusElement?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            author = info?.selectFirst("div.href-content.artist-content > a")?.text()?.takeIf { it != "Updating" }
            artist = info?.selectFirst("div.href-content.artist-content > a")?.text()?.takeIf { it != "Updating" }
            genre = info?.select("div.href-content.genres-content > a[href*=/manga-list/]")?.eachText()?.joinToString()
            thumbnail_url = document.selectFirst("div.summary_image > img")?.absUrl("src")
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private fun parseChapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")?.run {
            setUrlWithoutDomain(absUrl("href"))
            name = text()
        }
        date_upload = dateFormat.tryParseDate(element.selectFirst("span")?.text())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val contents = document.select("div.read-content.wleft img")
        if (contents.isEmpty()) {
            throw Exception("Unable to find script with image data")
        }
        return contents.mapIndexed { idx, image ->
            Page(idx, imageUrl = image.attr("src"))
        }
    }
}
