package eu.kanade.tachiyomi.extension.en.loadingartist

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class LoadingArtist : KeiSource() {

    override val supportsLatest = false

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    @Serializable
    private class Comic(
        val url: String,
        val title: String,
        val date: String = "",
        val section: String,
    )

    // Popular Section (list of comic archives by year)

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        listOf(
            SManga.create().apply {
                title = "Loading Artist"
                setUrlWithoutDomain("/archives")
                thumbnail_url = "$baseUrl/img/bg/logo-text_dark.png"
                artist = "Loading Artist"
                author = artist
                status = SManga.ONGOING
            },
        ),
        false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw Exception("Search not available for this source")

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val comics = client.get("$baseUrl/search.json").parseAs<List<Comic>>()
        val validTypes = listOf("comic", "game", "art")
        val chapterList = comics.filter { validTypes.any { type -> it.section == type } }.map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.url)
                name = it.title
                date_upload = dateFormat.tryParseDate(it.date)
            }
        }
        return SMangaUpdate(manga, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter)
        val imageUrl = client.get(url).asJsoup().selectFirst("div.main-image-container img")!!
            .attr("abs:src")
        return listOf(Page(0, url, imageUrl))
    }
}
