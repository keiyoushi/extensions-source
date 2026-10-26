package eu.kanade.tachiyomi.extension.ja.rawbaka

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RawBaka : KeiSource() {

    // The site ranks popularity only in a five-title homepage slider, so Popular lists the whole catalogue
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("manga/")
            if (page > 1) addPathSegments("page/$page/")
        }.build()
        return getMangaList(url)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latest_page", page.toString())
            .build()
        return getMangaList(url)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("s", query.trim())
            addQueryParameter("post_type", "manga")
        }.build()
        return getMangaList(url)
    }

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.manga-grid article.manga-card, article.search-result-card").map { card ->
            val link = card.selectFirst("h2 a, h3 a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("a.cover img")?.coverUrl()
            }
        }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    // Stored urls keep the decoded "/manga/<slug>/" path so existing library entries resolve
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document, manga), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = manga.apply {
        val hero = document.selectFirst("section.manga-hero")!!
        title = hero.selectFirst("h1.manga-title")!!.text()
        thumbnail_url = hero.selectFirst(".cover img")?.coverUrl()
        status = when (hero.selectFirst(".status-pill")?.text()?.lowercase()) {
            "ongoing", "on-going" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = hero.select(".manga-summary p").map { paragraph ->
            paragraph.select("br").forEach { it.replaceWith(TextNode("\n")) }
            paragraph.wholeText().lines().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
        }.filter(String::isNotEmpty).joinToString("\n\n").ifEmpty { null }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.chapter-list div.chapter-row").map { row ->
        val link = row.selectFirst("a")!!
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = DATE_FORMAT.tryParseDate(row.selectFirst("time")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.reader-images img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    // Widest srcset entry; search cards use a cropped thumbnail as src
    private fun Element.coverUrl(): String? = attrOrNull("srcset")
        ?.split(", ")
        ?.maxByOrNull { it.substringAfterLast(' ').removeSuffix("w").toIntOrNull() ?: 0 }
        ?.substringBeforeLast(' ')
        ?: attrOrNull("abs:src")

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    }
}
