package eu.kanade.tachiyomi.extension.en.darkscience

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
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DarkScience : KeiSource() {
    override val supportsLatest = false

    private fun initTheManga(manga: SManga): SManga = manga.apply {
        url = "/category/darkscience/"
        thumbnail_url = "https://dresdencodak.com/wp-content/uploads/2019/03/DC_CastIcon_Kimiko.png"
        title = name
        author = "Sen (A. Senna Diaz)"
        artist = "Sen (A. Senna Diaz)"
        description = """Scientist Kimiko Ross has a problem:
        | her money’s gone and a bank exploded her house. With no place
        | else to go, she travels to Nephilopolis, the city of giants –
        | built from the ruins of an ancient war and a fading memory of
        | tomorrow.\n Follow our cyborg hero as she attempts to survive the
        | bureaucratic behemoth with a little “help” from her “friends.”
        | And what exactly is Dark Science anyway?\nSupport the comic on
        | Patreon: https://www.patreon.com/dresdencodak
        """.trimMargin()
        genre = "Science Fiction, Mystery, LGBT+"
        status = SManga.ONGOING
        initialized = true
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        listOf(initTheManga(SManga.create())),
        false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = initTheManga(manga)
        if (!fetchChapters) return SMangaUpdate(updatedManga, chapters)

        val updatedChapters = mutableListOf<SChapter>()

        var archivePage: Document? = client.get(baseUrl + updatedManga.url).asJsoup()

        var chLast = 0.0F

        while (archivePage != null) {
            val nextArchivePageUrl = archivePage.selectFirst("""#nav-below .nav-previous > a""")
                ?.attr("href")
            val nextArchivePage = if (nextArchivePageUrl != null) {
                client.get(nextArchivePageUrl).asJsoup()
            } else {
                null
            }

            archivePage.select("""#content article header > h2 > a""").forEach {
                val chTitle = it.text()
                val chLink = it.attr("href")
                val chNum = chapterNumberRegex.find(chTitle)
                    ?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    ?: (chLast + 0.01F)

                updatedChapters.add(
                    SChapter.create().apply {
                        name = chTitle
                        chapter_number = chNum
                        date_upload = dateFormat.tryParseDate(chapterDateRegex.find(chLink)?.groupValues?.get(1))
                        setUrlWithoutDomain(chLink)
                    },
                )

                // This is a hack to make the app not think there’s missing chapters after
                // a title page.
                chLast = chNum
            }

            archivePage = nextArchivePage
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = listOf(
        Page(
            0,
            imageUrl = client.get(getChapterUrl(chapter)).asJsoup()
                .selectFirst("article.post img.aligncenter")!!
                .attr("src"),
        ),
    )

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
    private val chapterDateRegex = """/(\d\d\d\d/\d\d/\d\d)/""".toRegex()
    private val chapterNumberRegex = """Dark Science #(\d+)""".toRegex()
}
