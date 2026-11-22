package eu.kanade.tachiyomi.extension.es.novelaschafas

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.text.Normalizer
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

@Source
abstract class NovelasChafas : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(getMangaList(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // The blog's search only finds posts, and chapters are titled by their number
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getMangaList().filter { it.title.withoutAccents().contains(query.trim().withoutAccents(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host == baseUrl.toHttpUrl().host && url.encodedPath == MANGA_LIST_PATH) return getPopularManga(page)
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val mangas = getMangaList()
        mangas.firstOrNull { it.url == url.encodedPath }?.let {
            return getMangaUpdate(it, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        if (!POST_PATH.matches(url.encodedPath)) return null
        // Chapter posts don't link to their series, so the series pages are searched for the chapter
        for (manga in mangas) {
            val document = client.get(pageUrl(manga.url), Fetch.DOCUMENT).asJsoup()
            if (document.toSChapters().any { it.url == url.encodedPath }) {
                return document.toSManga().apply {
                    this.url = manga.url
                    initialized = true
                }
            }
        }
        return null
    }

    private suspend fun getMangaList(): List<SManga> = client.get(pageUrl(MANGA_LIST_PATH), Fetch.DOCUMENT).asJsoup()
        .select(".post-body a.tarjeta-novel")
        .map { card ->
            SManga.create().apply {
                url = card.absUrl("href").toHttpUrl().encodedPath
                title = card.selectFirst(".titulo-novel")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.toOriginalSize()
            }
        }
        .distinctBy { it.url }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(pageUrl(manga.url), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga().apply { url = manga.url }, document.toSChapters())
    }

    private fun Document.toSManga() = SManga.create().apply {
        val body = selectFirst(".post-body")!!
        title = selectFirst(".post-title")!!.text()
        thumbnail_url = body.selectFirst("img")?.absUrl("src")?.toOriginalSize()
        description = body.synopsis()
        status = when (STATUS_REGEX.find(body.text())?.groupValues?.get(1)?.lowercase()) {
            null -> SManga.UNKNOWN
            "finalizado" -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
        update_strategy = if (status == SManga.COMPLETED) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
    }

    // The series pages are written by hand: the synopsis is what comes before the first chapter link
    private fun Element.synopsis(): String? {
        val synopsis = clone().apply { select("script, style, summary").remove() }
        synopsis.select("a[href]").firstOrNull { it.chapterPath() != null }?.let { link ->
            var node: Node = link
            while (node !== synopsis) {
                while (node.nextSibling() != null) node.nextSibling()!!.remove()
                node = node.parentNode()!!
            }
            link.remove()
        }
        return MarkdownDescription.Builder().description(synopsis).build()
            ?.replace(CHAPTERS_HEADING_REGEX, "")
            ?.ifEmpty { null }
    }

    private fun Document.toSChapters() = selectFirst(".post-body")!!.select("a[href]")
        .mapNotNull { link ->
            val path = link.chapterPath() ?: return@mapNotNull null
            val text = link.textOrNull() ?: return@mapNotNull null
            SChapter.create().apply {
                url = path
                name = if (text.toDoubleOrNull() != null) "Capítulo $text" else text
                // The series pages don't date their chapters, only the post links do, by year and month
                date_upload = MONTH_FORMAT.tryParseDate(POST_PATH.matchEntire(path)!!.groupValues[1], BLOG_ZONE)
            }
        }
        .distinctBy { it.url }
        .reversed()

    private fun Element.chapterPath(): String? {
        val url = absUrl("href").toHttpUrlOrNull() ?: return null
        return url.encodedPath.takeIf { url.host == baseUrl.toHttpUrl().host && POST_PATH.matches(it) }
    }

    // Blogger sends the posts it marks as sensitive to a content warning, not their feed
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/feeds/posts/default".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .addQueryParameter("path", chapter.url)
            .build()
        val content = client.get(url, Fetch.XHR).parseAs<FeedResponseDto>().content
        return Jsoup.parseBodyFragment(content, baseUrl).select("img")
            .filterNot { it.isLinkToOtherSite() }
            .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src").toOriginalSize()) }
    }

    // Pages link to their full size image; an image linked elsewhere is a banner, like the translator's Ko-fi
    private fun Element.isLinkToOtherSite(): Boolean {
        val link = closest("a[href]")?.absUrl("href")?.toHttpUrlOrNull() ?: return false
        return link.host != absUrl("src").toHttpUrlOrNull()?.host
    }

    // The site links its mobile pages, where Blogger redirects phones anyway
    private fun pageUrl(path: String) = "$baseUrl$path?m=1"

    // Blogger serves an image at the size written before its file name, and s0 is the original
    private fun String.toOriginalSize() = replace(IMAGE_SIZE_REGEX, "/s0/")

    // The list drops some accents (Erase for Érase), so titles are compared without them
    private fun String.withoutAccents() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(ACCENT_REGEX, "")

    companion object {
        private const val MANGA_LIST_PATH = "/p/mangas.html"
        private val POST_PATH = Regex("""/(\d{4}/\d{2})/[^/]+\.html""")
        private val ACCENT_REGEX = Regex("""\p{Mn}+""")
        private val IMAGE_SIZE_REGEX = Regex("""/[swh]\d+[^/]*/(?=[^/]+$)""")
        private val STATUS_REGEX = Regex("""#(finalizado|en emisi[oó]n|en traducci[oó]n)""", RegexOption.IGNORE_CASE)
        private val CHAPTERS_HEADING_REGEX = Regex("""\s*\**cap[ií]tulos:?\**\s*$""", RegexOption.IGNORE_CASE)
        private val MONTH_FORMAT = DateTimeFormatterBuilder()
            .appendPattern("yyyy/MM")
            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
            .toFormatter(Locale.ROOT)
        private val BLOG_ZONE = ZoneId.of("America/Los_Angeles")
    }
}
