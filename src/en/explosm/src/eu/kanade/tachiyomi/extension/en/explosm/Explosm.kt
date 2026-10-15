package eu.kanade.tachiyomi.extension.en.explosm

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
import keiyoushi.utils.tryParseDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Explosm : KeiSource() {

    override val supportsLatest = false

    private val archivePage get() = "$baseUrl/comics"

    private suspend fun getArchiveAllYears(): Map<String, Map<String, List<ComicDto>>> {
        val jsonPath = client.get(archivePage).asJsoup()
            .select("head > script").last()?.attr("src")
            ?.replace("static", "data")
            ?.replaceAfterLast("/", "comics.json")
            ?: throw Exception("Error at last() in getArchiveAllYears")
        return client.get(baseUrl + jsonPath).parseAs<ComicsResponse>().pageProps.comicArchiveData
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val eachYearAsAManga = getArchiveAllYears().keys
            .map { year ->
                SManga.create().apply {
                    initialized = true
                    title = "C&H $year"
                    url = year
                    thumbnail_url = "https://vhx.imgix.net/vitalyuncensored/assets/13ea3806-5ebf-4987-bcf1-82af2b689f77/S2E4_Still1.jpg"
                    author = "Explosm.net"
                }
            }
            .reversed()

        return MangasPage(eachYearAsAManga, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    // Details

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics#${manga.url}-01"

    // Chapters

    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        var chapterCount = 0F
        val chapterList = getArchiveAllYears()[manga.url]
            ?.values
            ?.flatMap { month ->
                month.map { comic ->
                    chapterCount++
                    SChapter.create().apply {
                        name = comic.slug
                        // we get the url for page.imageurl here
                        val imageUrl = when {
                            comic.fileStatic != null -> comic.fileStatic
                            comic.file?.startsWith("http") == true -> comic.file
                            else -> "https://files.explosm.net/comics/${comic.file}"
                        }
                        url = "/comics/${comic.slug}#$imageUrl"
                        date_upload = date.tryParseDateTime(comic.publishAt)
                        scanlator = comic.authorName
                        chapter_number = chapterCount // so no "missing chapters" warning in app
                    }
                }
            }
            ?.reversed()
            ?: throw Exception("Error with main jsonObject")

        return SMangaUpdate(manga, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> = listOf(Page(0, "", chapter.url.substringAfter("#")))
}
