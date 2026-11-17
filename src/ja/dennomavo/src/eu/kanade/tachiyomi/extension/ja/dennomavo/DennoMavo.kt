package eu.kanade.tachiyomi.extension.ja.dennomavo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class DennoMavo : KeiSource() {

    // The home page only lists the latest episodes, picked by hand
    override val supportsLatest = false

    // Every title is on one page
    override suspend fun getPopularManga(page: Int) = MangasPage(parseTitleCards(client.get("$baseUrl/all-list.php", Fetch.DOCUMENT).asJsoup()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder().addQueryParameter("keywords", query).build()
        return MangasPage(parseTitleCards(client.get(url, Fetch.DOCUMENT).asJsoup()), false)
    }

    private fun parseTitleCards(document: Document): List<SManga> = document.select("a[href^=title.php]:has(p.mangatitle)").map { card ->
        SManga.create().apply {
            url = card.absUrl("href").toHttpUrl().queryParameter("title")!!
            title = card.selectFirst("p.mangatitle")!!.text()
            author = card.selectFirst("p.authors")?.text()?.toAuthors()
            thumbnail_url = card.selectFirst("img")?.absUrl("src")
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/title.php?title=${manga.url}"

    // Details and episodes are on the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document, manga.url), parseChapters(document))
    }

    // Episode links lead to their title
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = when (url.pathSegments.last()) {
            "title.php" -> url.queryParameter("title")
            "viewer.php", "pcviewer.php" -> client.get("$baseUrl/viewer.php?id=${url.queryParameter("id")}", Fetch.DOCUMENT).asJsoup()
                .selectFirst("#menu a[href^=title.php]")?.absUrl("href")?.toHttpUrl()?.queryParameter("title")
            else -> null
        } ?: return null
        val document = client.get("$baseUrl/title.php?title=$id", Fetch.DOCUMENT).asJsoup()
        // A title that doesn't exist is an empty page
        if (document.selectFirst("#menu strong") == null) return null
        return parseDetails(document, id).apply { initialized = true }
    }

    private fun parseDetails(document: Document, id: String) = SManga.create().apply {
        url = id
        val header = document.selectFirst("#menu span")!!
        title = header.selectFirst("strong")!!.text()
        author = header.ownText().toAuthors()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("div.description"))
            .build()
        thumbnail_url = document.selectFirst("h2 > img")?.absUrl("src")
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("#title ul.manga > a[href^=viewer.php]").map { episode ->
        SChapter.create().apply {
            url = episode.absUrl("href").toHttpUrl().queryParameter("id")!!
            name = episode.selectFirst("p.mangatitle")!!.text()
            date_upload = DATE_FORMAT.tryParseDate(episode.selectFirst("p.up-date")?.text()?.substringAfter('：'), ZONE)
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/viewer.php?id=${chapter.url}"

    // Pages after the first two are lazy-loaded from data-original
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("div.page img.img-responsive")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("data-original").ifEmpty { img.absUrl("src") }) }

    // Writer and artist are joined by "×"
    private fun String.toAuthors() = split('×').map(String::trim).filter(String::isNotEmpty).joinToString().ifEmpty { null }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d")
        private val ZONE = ZoneId.of("Asia/Tokyo")
    }
}
