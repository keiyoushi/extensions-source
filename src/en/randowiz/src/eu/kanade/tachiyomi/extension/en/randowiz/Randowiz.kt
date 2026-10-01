package eu.kanade.tachiyomi.extension.en.randowiz

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
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Randowiz : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        listOf(
            SManga.create().apply {
                title = "Randowiz: We live in an MMO!?"
                artist = "Randowiz"
                author = "Randowiz"
                status = SManga.ONGOING
                url = "/category/we-live-in-an-mmo/"
                description = "The world of 'Mamuon' where players and NPC's live together in harmony. Or do they? DO THEY?"
                thumbnail_url = "https://i0.wp.com/randowis.com/wp-content/uploads/2016/02/MMO_CHP_001_CSP_000.jpg?resize=800%2C800&ssl=1"
            },
            SManga.create().apply {
                title = "Randowiz: Short comics"
                artist = "Randowiz"
                author = "Randowiz"
                status = SManga.ONGOING
                url = "/category/short-comics/"
                description = "So short that i have to compensate.."
                thumbnail_url = "https://i0.wp.com/randowis.com/wp-content/uploads/2021/10/Images_PNGs_Site_BOT-SUPPORT.png"
            },
            SManga.create().apply {
                title = "Randowiz: Illustations"
                artist = "Randowiz"
                author = "Randowiz"
                status = SManga.ONGOING
                url = "/category/art/"
                description = "You like draw? I give you draw."
                thumbnail_url = "https://i0.wp.com/randowis.com/wp-content/uploads/2021/05/colour-studies-021-post.jpg"
            },
        ),
        false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(
        getPopularManga(page).mangas.filter { manga ->
            manga.title.contains(query, ignoreCase = true)
        },
        false,
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val chapterList = mutableListOf<SChapter>()
        var currentDocument = client.get(getMangaUrl(manga)).asJsoup()

        while (true) {
            chapterList += currentDocument.select(".has-post-thumbnail").map { element ->
                SChapter.create().apply {
                    val linkTag = element.selectFirst(".elementor-post__title a")!!
                    name = linkTag.text()
                    setUrlWithoutDomain(linkTag.attr("abs:href"))
                    date_upload = dateFormat.tryParseDate(element.selectFirst(".elementor-post-date")?.text())
                }
            }

            val nextUrl = currentDocument.selectFirst(".next")?.attr("abs:href")
            if (nextUrl.isNullOrEmpty()) break

            currentDocument = client.get(nextUrl).asJsoup()
        }

        return SMangaUpdate(
            manga,
            chapterList.mapIndexed { i, chapter ->
                chapter.apply { chapter_number = chapterList.size.toFloat() - i }
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup().select(".elementor-widget-theme-post-content img").mapIndexed { index, img ->
        Page(index, imageUrl = img.attr("abs:src"))
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    }
}
