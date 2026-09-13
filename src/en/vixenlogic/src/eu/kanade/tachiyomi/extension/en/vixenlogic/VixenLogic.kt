package eu.kanade.tachiyomi.extension.en.vixenlogic

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
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class VixenLogic : KeiSource() {
    private fun manga(): SManga = SManga.create().apply {
        title = "Vixen Logic"
        thumbnail_url = "$baseUrl/wp-content/uploads/2026/06/VL_Cover_Toocheke.png"
        author = "tootaloo and foxboy83"
        status = SManga.UNKNOWN
        url = "/"
        initialized = true
    }

    override val supportsLatest: Boolean = false

    private val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

    private fun parseDate(dateStr: String): Long = when (dateStr.lowercase()) {
        "today" -> LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        "yesterday" -> LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else -> runCatching {
            LocalDate.parse(dateStr, dateFormat).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        mangas = listOf(manga()),
        hasNextPage = false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(
        mangas = listOf(manga()),
        hasNextPage = false,
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) {
            return SMangaUpdate(manga, chapters)
        }

        val archives = client.get("$baseUrl/archives/").asJsoup()
        val comicItems = archives.select(".comic-item")

        val chapters = comicItems.map { ci ->
            val title = ci.select(".comic-title").text()
            val dateText = ci.select(".comic-post-date").text()
            val url = ci.parent()!!.absUrl("href")
            SChapter.create().apply {
                setUrlWithoutDomain(url)
                name = title
                date_upload = parseDate(dateText)
            }
        }

        return SMangaUpdate(
            manga = manga,
            chapters = chapters,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl/${chapter.url}", headers).asJsoup()
        return document.select("#comic p a img").mapIndexed { index, img ->
            val imgUrl = img.absUrl("src").ifEmpty { img.attr("src") }
            Page(index, imageUrl = imgUrl)
        }
    }
}
