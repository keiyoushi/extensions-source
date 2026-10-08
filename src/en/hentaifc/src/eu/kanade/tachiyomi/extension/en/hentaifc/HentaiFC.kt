package eu.kanade.tachiyomi.extension.en.hentaifc

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HentaiFC : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    // The site only publishes a "Latest Updates" listing, so it is served here.
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page"
        val document = client.get(url).asJsoup()
        val mangas = document.select("#book_list .wrap_item").map(::parseEntry)
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("search_by", "title")
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("#book_list .wrap_item").map(::parseEntry)
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl/${manga.url}").asJsoup()

        val updatedManga = manga.apply {
            val rawTitle = document.selectFirst("h1.heading")?.text().orEmpty().trim()
            check(rawTitle.isNotBlank()) { "Empty title for ${manga.url}" }
            title = rawTitle
            author = document.select(".d-cell.value.authors a.author").eachText()
                .joinToString(", ").ifEmpty { null }
            artist = author
            genre = document.select(".genres a[href*=/tag/]").eachText()
                .joinToString(", ").ifEmpty { null }
            if (thumbnail_url.isNullOrEmpty()) {
                thumbnail_url = document.selectFirst(".thumbs .wrap_item img")
                    ?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
                    ?.ifEmpty { null }
            }
            status = SManga.COMPLETED
        }

        return SMangaUpdate(updatedManga, parseChapters(document, manga.url))
    }

    private fun parseChapters(document: Document, galleryUrl: String): List<SChapter> {
        val galleryId = galleryUrl.substringAfterLast("/")
        val date = parseRelativeDate(document.selectFirst(".d-cell.value.updateAt")?.text())
        return document.select(".thumbs .wrap_item a[href]")
            .map { it.attr("abs:href") }
            .filter { it.contains(Regex("/e/$galleryId/c\\d+")) }
            .map { it.substringBefore("#").substringBefore("?") }
            .distinct()
            .mapNotNull { href ->
                val number = href.substringAfterLast("/c").toFloatOrNull() ?: return@mapNotNull null
                SChapter.create().apply {
                    url = href.toHttpUrl().encodedPath.removePrefix("/")
                    name = "Chapter ${number.toInt()}"
                    chapter_number = number
                    date_upload = date
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    // The reader page itself is JS-rendered, so page images are read from the
    // gallery page's thumbnail grid, which carries the full image list.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterNumber = chapter.url.substringAfterLast("/c")
        val galleryUrl = chapter.url.substringBeforeLast("/c")
        return client.get("$baseUrl/$galleryUrl").asJsoup()
            .select(".thumbs .wrap_item")
            .mapNotNull { item ->
                val href = item.selectFirst("a[href]")?.attr("abs:href")
                    ?.substringBefore("#")?.substringBefore("?") ?: return@mapNotNull null
                if (href.substringAfterLast("/c") != chapterNumber) return@mapNotNull null
                val imageUrl = item.selectFirst("img")?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                }.orEmpty()
                if (imageUrl.isEmpty()) return@mapNotNull null
                imageUrl
            }
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private fun parseEntry(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("h3.title a")!!
        url = link.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        val rawTitle = link.text().trim()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = element.selectFirst(".wrap_img img")?.attr("src")?.ifEmpty { null }
        genre = element.select(".genres a").eachText().joinToString(", ").ifEmpty { null }
    }

    private fun parseRelativeDate(text: String?): Long {
        val match = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")
            .find(text.orEmpty()) ?: return 0L
        val field = when (match.groupValues[2]) {
            "second" -> Calendar.SECOND
            "minute" -> Calendar.MINUTE
            "hour" -> Calendar.HOUR_OF_DAY
            "day" -> Calendar.DAY_OF_YEAR
            "week" -> Calendar.WEEK_OF_YEAR
            "month" -> Calendar.MONTH
            else -> Calendar.YEAR
        }
        return Calendar.getInstance()
            .apply { add(field, -match.groupValues[1].toInt()) }
            .timeInMillis
    }
}
