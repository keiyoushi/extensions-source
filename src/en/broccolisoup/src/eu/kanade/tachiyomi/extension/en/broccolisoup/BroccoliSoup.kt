package eu.kanade.tachiyomi.extension.en.broccolisoup

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class BroccoliSoup : HttpSource() {

    override val name = "Broccoli Soup"

    override val baseUrl = "https://politeandgood.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor(TextInterceptor()).build()

    // Popular

    private fun createManga(): SManga = SManga.create().apply {
        title = "Broccoli Soup"
        url = "/comic/archive"
        author = "Secret Pie"
        artist = author
        description = " Hello there! How is the Weather? This comic is made by me, Secret Pie. I am a pie with legs who draws comics and makes music. I am also an entomologist."
        thumbnail_url = "https://politeandgood.com/assets/images/static/Bocki%20(correct%20size).png"
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(createManga()), false))

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(createManga().apply { initialized = true })

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // Chapters

    private val characterSummaryPathSlug = "comic-characters"

    private fun createCharacterSummaryChapter(): SChapter = SChapter.create().apply {
        url = "/$characterSummaryPathSlug"
        name = "Characters"
        chapter_number = 0f
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // Keep track of the last-used chapter number in each "arc" of chapters
        val arcIndexMap = mutableMapOf<String, Int>()

        // Add the character summary page as a chapter
        val chaptersList = mutableListOf(createCharacterSummaryChapter())

        response.asJsoup().select("li.archive-marker")
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

    private fun characterSummaryPageListParse(response: Response): List<Page> = response.asJsoup().select("section.static-block:has(figure, .block-content)")
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

    override fun pageListParse(response: Response): List<Page> {
        if (response.request.url.pathSegments.lastOrNull() == characterSummaryPathSlug) {
            // The character summary page needs special parsing
            return characterSummaryPageListParse(response)
        }

        return response.asJsoup().select("#comic img")
            .mapIndexed { index, element ->
                Page(index, "", element.attr("abs:src"))
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
