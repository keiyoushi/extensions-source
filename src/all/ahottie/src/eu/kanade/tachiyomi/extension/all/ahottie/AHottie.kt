package eu.kanade.tachiyomi.extension.all.ahottie

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class AHottie : KeiSource() {
    override val supportsLatest = false

    // ========================= Popular =========================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()
        return popularMangaParse(client.get(url).asJsoup())
    }

    private fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select("#main > div > div").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a")!!
                val titleEl = element.selectFirst("h2") ?: throw Exception("Title not found")
                title = titleEl.text()
                thumbnail_url = element.selectFirst(".relative img")?.absUrl("src")
                genre = element.select(".flex a").joinToString(", ") { it.text() }
                setUrlWithoutDomain(link.absUrl("href"))
                initialized = true
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("kw", query)
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()

        if (document.selectFirst("h1") != null && document.selectFirst("div.pl-3 > a") != null) {
            val manga = mangaDetailsParse(document).apply {
                this.url = url.encodedPath
            }
            return MangasPage(listOf(manga), false)
        }

        return popularMangaParse(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url).asJsoup()
        if (document.selectFirst("h1") != null && document.selectFirst("div.pl-3 > a") != null) {
            return mangaDetailsParse(document).apply {
                this.url = url.encodedPath
            }
        }
        return null
    }

    // ========================= Details =========================

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val titleEl = document.selectFirst("h1") ?: throw Exception("Title not found")
        title = titleEl.text()
        genre = document.select("div.pl-3 > a").joinToString(", ") { it.text() }
        initialized = true
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        val updatedManga = mangaDetailsParse(document)

        val updatedChapters = listOf(
            SChapter.create().apply {
                val link = document.selectFirst("link[rel=canonical]") ?: throw Exception("Chapter link not found")
                setUrlWithoutDomain(link.absUrl("href"))
                chapter_number = 0F
                name = "GALLERY"
                date_upload = document.selectFirst("time")?.text()?.let { parseDate(it) } ?: 0L
            },
        )

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseDate(dateStr: String): Long = runCatching {
        LocalDate.parse(dateStr, DATE_FORMATTER)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrDefault(0L)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ========================= Chapters =========================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ========================= Pages =========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = mutableListOf<Page>()
        var doc = client.get(baseUrl + chapter.url).asJsoup()
        while (true) {
            doc.select("#main img.block").forEach {
                pages.add(Page(pages.size, imageUrl = it.absUrl("src")))
            }
            val nextPageUrl = doc.selectFirst("a[rel=next]")?.absUrl("href") ?: ""
            if (nextPageUrl.isEmpty()) break
            doc = client.get(nextPageUrl).asJsoup()
        }
        return pages
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    }
}
