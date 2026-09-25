package eu.kanade.tachiyomi.extension.en.kunmangaonline

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class KunMangaOnline : MadaraNoAjax() {
    override val supportsPostId = false
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    private val apiHeaders get() = headersBuilder()
        .add("Accept", "application/json")
        .build()

    override fun archiveSelector() = ".c-tabs-item__content, .page-item-detail"
    override val archiveUrlSelector = ".post-title a, h3.h4 a"
    override fun nextPageSelector() = "a[aria-label=Next]"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val slug = "$baseUrl$mangaPath".toHttpUrl().pathSegments.getOrNull(1) ?: return emptyList()

        val firstUrl = "$baseUrl/api/comics/$slug/chapters?page=1&per_page=$CHAPTERS_PER_PAGE&order=desc"
        val firstResponse = client.get(firstUrl, apiHeaders)
        val firstPage = firstResponse.parseAs<ChapterListResponse>().data

        val allChapters = firstPage.chapters.map { it.toSChapter(slug) }.toMutableList()

        if (firstPage.lastPage > 1) {
            coroutineScope {
                (2..firstPage.lastPage).map { page ->
                    async {
                        val apiUrl = firstResponse.request.url.newBuilder()
                            .setQueryParameter("page", page.toString())
                            .build()
                        client.get(apiUrl, apiHeaders).parseAs<ChapterListResponse>().data.chapters
                    }
                }.awaitAll().forEach { chapters ->
                    allChapters.addAll(chapters.map { it.toSChapter(slug) })
                }
            }
        }

        return allChapters
    }

    override fun imageFromElement(element: Element) = listOf("data-backup", "src", "data-src", "data-lazy-src", "data-aload")
        .map { element.absUrl(it) }
        .firstOrNull { it.startsWith("http") && !it.contains("${baseUrl.toHttpUrl().host}/thumb/") }

    override fun imageRequest(page: Page) = super.imageRequest(page).newBuilder().header("Referer", "$baseUrl/").build()

    companion object {
        private const val POSTS_PER_PAGE = 20
        private const val CHAPTERS_PER_PAGE = 50
    }
}
