package eu.kanade.tachiyomi.extension.es.jentranslation

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaEntryDto
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class JenTranslation : ZeistManga() {

    // The front page only lists a few trending series, so the series feed is the catalogue
    override val supportsLatest = false

    // Some titles end with spaces in the feed
    override fun parseSearchManga(response: Response): MangasPage = super.parseSearchManga(response).apply {
        mangas.forEach { it.title = it.title.trim() }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments
        if (url.host == baseUrl.toHttpUrl().host && segments.size == 3 && segments[0] == "search" && segments[1] == "label") {
            return getSearchMangaList(page, "label:\"${segments[2]}\"", FilterList())
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !POST_PATH_REGEX.matches(url.encodedPath)) return null
        val post = getPost(url.encodedPath)
        val labels = post.labels()
        val series = if (chapterCategory in labels) findSeries(labels) else post.takeIf { SERIES_LABEL in labels }
        return series?.toSManga(baseUrl)?.apply { title = title.trim() }
    }

    // A chapter post carries the label that its series puts in .chapter_get
    private suspend fun findSeries(chapterLabels: List<String>): ZeistMangaEntryDto? {
        val query = chapterLabels.filter { it != chapterCategory }
            .joinToString(" ", "label:\"$SERIES_LABEL\" {", "}") { "label:\"$it\"" }
        val url = apiUrl().addQueryParameter("q", query).build()
        return client.get(url, Fetch.XHR).parseAs<ZeistMangaDto>().feed?.entry.orEmpty()
            .firstOrNull { it.document().selectFirst(".chapter_get")?.attr("data-labelchapter") in chapterLabels }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val badges = document.selectFirst("#post-title + div")
        title = document.selectFirst("#post-title")!!.text()
        thumbnail_url = document.selectFirst("[data-separator] .separator img")?.absUrl("src")?.toOriginalSize()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#syn_bod"), keepNewlines = true)
            .field("Year", document.selectFirst("#trelease")?.text())
            .build()
        genre = badges?.select("span[type], a[rel=tag]")?.joinToString { it.text() }?.ifEmpty { null }
        status = parseStatus(badges?.selectFirst("span.capitalize")?.text().orEmpty())
    }

    // The summary feed lists the chapters without their pages (85 KB instead of 2.4 MB for 44 chapters)
    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String = "$baseUrl/feeds/posts/summary/-/$chapterCategory".toHttpUrl()
        .newBuilder()
        .addPathSegment(doc.selectFirst(".chapter_get")!!.attr("data-labelchapter"))
        .addQueryParameter("alt", "json")
        .build()
        .toString()

    // Blogger sends the posts it marks as sensitive to a content warning, not their feed
    override suspend fun getPageList(chapter: SChapter): List<Page> = getPost(chapter.url).document().select("img[src]").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("src").toOriginalSize())
    }

    override val hasFilters = true
    override val hasTypeFilter = false
    override val hasLanguageFilter = false

    override fun getStatusList() = listOf(
        Status(intl["all"], ""),
        Status(intl["status_ongoing"], "Ongoing"),
        Status(intl["status_completed"], "Completed"),
    )

    override fun getGenreList() = GENRES.map { Genre(it, it) }

    private suspend fun getPost(path: String): ZeistMangaEntryDto {
        val url = apiUrl().addQueryParameter("path", path).build()
        return client.get(url, Fetch.XHR).parseAs<ZeistMangaDto>().feed!!.entry!!.single()
    }

    private fun ZeistMangaEntryDto.labels() = category.orEmpty().map { it.term }

    private fun ZeistMangaEntryDto.document() = Jsoup.parseBodyFragment(content!!.t, baseUrl)

    // Blogger serves an image at the size written before its file name, and s0 is the original
    private fun String.toOriginalSize() = replace(IMAGE_SIZE_REGEX, "/s0/")

    companion object {
        private const val SERIES_LABEL = "Series"
        private val POST_PATH_REGEX = Regex("""/\d{4}/\d{2}/[^/]+\.html""")
        private val IMAGE_SIZE_REGEX = Regex("""/[swh]\d+[^/]*/(?=[^/]+$)""")
    }
}
