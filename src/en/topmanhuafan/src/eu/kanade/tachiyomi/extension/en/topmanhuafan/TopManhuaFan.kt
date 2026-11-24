package eu.kanade.tachiyomi.extension.en.topmanhuafan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.escapeMarkdown
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TopManhuaFan : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT)
    override val mangaSubString = "manhua"
    private var firstSearchPath: String? = null

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return super.getSearchMangaList(page, query, filters)

        // Search repeats page 1 on /page/N/
        val url = searchUrlBuilder(1, query).apply {
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val cards = parseSearchCards(document)

        // Past its last page search repeats page 1
        val firstPath = cards.firstOrNull()?.path
        if (page == 1) {
            firstSearchPath = firstPath
        } else if (firstPath == firstSearchPath) {
            return MangasPage(emptyList(), false)
        }

        // Search cards have no post id
        val mangas = coroutineScope {
            cards.map { async { getMangaByUrl(baseUrl.toHttpUrl().resolve(it.path)!!) } }.awaitAll()
        }
        return MangasPage(mangas.filterNotNull(), page < LAST_SEARCH_PAGE && document.selectFirst(nextPageSelector()) != null)
    }

    // The first paragraph repeats the title, and the synopsis can open with a "Read <title>" line
    override fun descriptionParagraphs(document: Document): List<String> {
        val title = document.selectFirst(mangaDetailsSelectorTitle)?.ownText()
        val seoLines = setOf("Read manga $title", "Read $title Manhua")
        val paragraphs = document.selectFirst(mangaDetailsSelectorDescription)?.select("p") ?: return emptyList()
        return paragraphs
            .map { it.wholeText().trim().lines().dropWhile { line -> line.trim() in seoLines }.joinToString("\n").trim() }
            .filterNot { it.isEmpty() || it == title }
            .map(String::escapeMarkdown)
    }

    override fun chapterListSelector() = "div.wp-manga-chapter"

    // Image hosts refuse the theme's chapter Referer, and images sent with Origin miss the Cloudflare cache
    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(imageFetch.from(page.url))
        .build()

    companion object {
        // Past page 100 search repeats page 100
        private const val LAST_SEARCH_PAGE = 100
    }
}
