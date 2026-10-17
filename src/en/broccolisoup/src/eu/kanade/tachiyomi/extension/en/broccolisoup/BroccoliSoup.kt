package eu.kanade.tachiyomi.extension.en.broccolisoup

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class BroccoliSoup : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(TextInterceptor())

    // Popular

    private fun createManga(): SManga = SManga.create().apply {
        title = "Broccoli Soup"
        url = "/comic/archive"
        author = "Secret Pie"
        artist = author
        description = " Hello there! How is the Weather? This comic is made by me, Secret Pie. I am a pie with legs who draws comics and makes music. I am also an entomologist."
        thumbnail_url = "https://politeandgood.com/assets/images/static/Bocki%20(correct%20size).png"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val chapterList = if (fetchChapters) {
            chapterListParse(client.get(getMangaUrl(manga)).asJsoup())
        } else {
            chapters
        }

        return SMangaUpdate(createManga(), chapterList)
    }

    // Chapters

    private val characterSummaryPathSlug = "comic-characters"

    private fun createCharacterSummaryChapter(): SChapter = SChapter.create().apply {
        url = "/$characterSummaryPathSlug"
        name = "Characters"
        chapter_number = 0f
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        // Keep track of the last-used chapter number in each "arc" of chapters
        val arcIndexMap = mutableMapOf<String, Int>()

        // Add the character summary page as a chapter
        val chaptersList = mutableListOf(createCharacterSummaryChapter())

        document.select("li.archive-marker")
            .flatMapTo(chaptersList) { groupElement ->
                val arcTitle = groupElement.selectFirst(".archive-header .marker-title")?.text()
                groupElement.select("li.archive-page")
                    .mapNotNull { chapterElement ->
                        // Skip chapters elements that are missing the required subelements
                        val linkElement = chapterElement.selectFirst("a") ?: return@mapNotNull null
                        val titleElement = linkElement.selectFirst("span.page-title") ?: return@mapNotNull null

                        val url = linkElement.attr("href")
                        val chapterNumber = url.substringAfterLast("/").toIntOrNull()

                        // Construct a title from the chapter number, chapter title, arc title, and
                        // the chapter number within the current arc.
                        // E.g. "98: Apologetics (VOID #43)"
                        val title = listOfNotNull(
                            chapterNumber?.let { "$chapterNumber:" },
                            titleElement.text(),
                            arcTitle?.let {
                                val newIndex = 1 + (arcIndexMap[arcTitle] ?: 0)
                                arcIndexMap[arcTitle] = newIndex
                                "($arcTitle #$newIndex)"
                            },
                        ).joinToString(separator = " ")

                        SChapter.create().apply {
                            setUrlWithoutDomain(url)

                            name = title

                            if (chapterNumber != null) {
                                // Set the chapter number if we have one
                                chapter_number = chapterNumber.toFloat()
                            }

                            // The chapter list doesn't have the upload date, so we can't set them
                        }
                    }
            }
        // Reverse the list since "source" ordering is expected to have the latest
        // chapter first in the list.
        chaptersList.reverse()

        return chaptersList
    }

    // Pages

    private fun characterSummaryPageListParse(document: Document): List<Page> = document.select("section.static-block:has(figure, .block-content)")
        .flatMap { sectionElement ->
            val headerText = sectionElement
                .selectFirst("section > :is(h1, h2, h3, h4)")
                ?.text()?.trim()

            val bodyText = sectionElement.selectFirst("div.block-content")
                ?.text()?.trim()

            val imageUrl = sectionElement.selectFirst("figure img")
                ?.attr("abs:src")

            var pageIndex = 0
            listOfNotNull<Page>(
                // The character's name and summary
                if (headerText != null || bodyText != null) {
                    val textUrl = TextInterceptorHelper.createUrl(headerText ?: "", bodyText ?: "")
                    Page(pageIndex++, "", textUrl)
                } else {
                    null
                },
                // The character's image
                imageUrl?.let { Page(pageIndex++, "", imageUrl) },
            )
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val isCharacterSummary = response.request.url.pathSegments.lastOrNull() == characterSummaryPathSlug
        val document = response.asJsoup()

        if (isCharacterSummary) {
            // The character summary page needs special parsing
            return characterSummaryPageListParse(document)
        }

        return document.select("#comic img")
            .mapIndexed { index, element ->
                Page(index, "", element.attr("abs:src"))
            }
    }
}
