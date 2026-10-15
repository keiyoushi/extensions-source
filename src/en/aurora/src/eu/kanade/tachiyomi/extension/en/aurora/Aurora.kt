package eu.kanade.tachiyomi.extension.en.aurora

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Aurora : KeiSource() {

    override val supportsLatest = false
    private val dateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.US)

    /**
     * Because the comic is updated 1 page at a time the chapters are turned into different mangas
     * so that the pages can be turned into different chapters which can be automatically updated by
     * Tachiyomi.
     *
     * @return List of all Chapters as separate mangas
     */
    override suspend fun getPopularManga(page: Int): MangasPage {
        val chapterOverviewDoc = client.get("$baseUrl/archive/").asJsoup()
        val chapterBlockElements = chapterOverviewDoc.select(".wp-block-image:has(a)")
        val mangasFromChapters = chapterBlockElements
            .mapIndexed { chapterIndex, chapter ->
                val chapterOverviewLink = chapter.selectFirst("a")!!
                val chapterOverviewUrl = chapterOverviewLink.attr("href")
                val chapterTitle = "$name - ${chapterOverviewLink.text()}"
                val chapterThumbnail = chapter.selectFirst("img")!!.attr("src")

                SManga.create().apply {
                    setUrlWithoutDomain(chapterOverviewUrl)
                    title = chapterTitle
                    author = "OSP-Red"
                    description = auroraDescription
                    genre = "fantasy"
                    // this will mark every chapter except the last one as completed
                    status =
                        if (chapterIndex >= chapterBlockElements.size - 1) {
                            SManga.UNKNOWN
                        } else {
                            SManga.COMPLETED
                        }
                    thumbnail_url = chapterThumbnail
                    initialized = true
                }
            }

        return MangasPage(mangasFromChapters, false)
    }

    private val auroraDescription = """
    Aurora is a fantasy webcomic (updates M/W/F) written and illustrated by Red, better known for her work on the YouTube channel “Overly Sarcastic Productions.” It’s been in the works for over a decade, and she’s finally decided to stop putting it off.

    If you’d like to discuss the comic, it now has a subreddit, as well as a dedicated twitter and a tumblr where you can ask questions. There’s also a dedicated room on the channel discord for conversations about it!

    Find Red’s general ramblings on Twitter, alongside her cohost Blue, at OSPYouTube.
    """.trimIndent()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedChapters = if (fetchChapters) {
            fetchChapterListTR(baseUrl + manga.url)
        } else {
            chapters
        }
        return SMangaUpdate(manga, updatedChapters)
    }

    private suspend fun fetchChapterListTR(currentUrl: String): List<SChapter> {
        val firstPage = client.get(currentUrl).asJsoup()

        return coroutineScope {
            listOf(async { firstPage }) +
                firstPage.select("#paginav a[title]").drop(1)
                    .map { async { client.get(it.attr("href")).asJsoup() } }
        }.awaitAll().flatMap { page ->
            page.select(".post-content").map { postContent ->
                val chapterUrl = postContent.select("a.webcomic-link").attr("href")
                val title = postContent.select(".post-title a").text()
                val chapterNo = title.substringAfter('.').substringBefore('-').toFloat()
                val date = dateFormat.tryParseDate(
                    postContent.select(".post-date").text(),
                )

                SChapter.create().apply {
                    setUrlWithoutDomain(chapterUrl)
                    name = title
                    chapter_number = chapterNo
                    date_upload = date
                }
            }
        }
            .toMutableList().reversed()
    }

    override suspend fun getPageList(chapter: SChapter) = client.get(baseUrl + chapter.url).asJsoup().select(
        ".webcomic-media .webcomic-link .attachment-full",
    ).mapIndexed { idx, page ->
        Page(idx, imageUrl = page.attr("src"))
    }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
}
