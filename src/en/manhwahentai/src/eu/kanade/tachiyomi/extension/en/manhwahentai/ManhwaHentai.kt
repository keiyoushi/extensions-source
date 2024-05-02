package eu.kanade.tachiyomi.extension.en.manhwahentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaHentai : Madara(
    "Manhwa Hentai",
    "https://manhwahentai.to",
    "en",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("eb")),
) {
    override val mangaDetailsSelectorTitle = "div.post-title .h1"
    override val mangaDetailsSelectorArtist = "div.post-tax-wp-manga-artist a .tag-name"
    override val mangaDetailsSelectorDescription = ".post-meta-title:contains(Description:) + .post-meta-value"
    override val mangaDetailsSelectorGenre = "div.post-tax-wp-manga-category a .tag-name"
    override val mangaDetailsSelectorTag = "div.post-tax-wp-manga-tags a .tag-name"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "pornhwa"

    override fun chapterListSelector() = "li[wire:key=chapter]"
    override fun chapterDateSelector() = "div.release p"

    override fun oldXhrChaptersRequest(mangaId: String): Request {
        val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "get-all-chapters-list")
            .addQueryParameter("post_id", mangaId)
            .addQueryParameter("chapters_per_page", "")
            .addQueryParameter("offset", "0")
            .build()

        return GET(url, xhrHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        launchIO { countViews(document) }

        val mangaId = document
            .selectFirst("a[data-action=bookmark]")
            ?.attr("data-post")
            ?: throw Exception("Failed to find mangaId")

        val xhrRequest = oldXhrChaptersRequest(mangaId)
        client.newCall(xhrRequest).execute().use { xhrResponse ->
            val xhrDocument = Jsoup.parse(xhrResponse.parseAs<XhrResponseDto>().data, response.request.url.toString())
            val chapterElements = xhrDocument.select(chapterListSelector())
            return chapterElements.map(::chapterFromElement).reversed()
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            chapter.url = selectFirst(chapterUrlSelector)!!.attr("abs:href").let {
                if (it.endsWith("/")) it else "$it/"
            }
            chapter.name = selectFirst("div:not(.release) p")!!.text()
            chapter.date_upload = parseChapterDate(selectFirst(chapterDateSelector())?.text())
        }

        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val pages = document.selectFirst("#chapter_preloaded_images")!!.data()
            .substringAfter("chapter_preloaded_images=")
            .removeSuffix(",]")
            .let { json.decodeFromString<List<PageDto>>("$it]") }

        return pages.mapIndexed { idx, page ->
            Page(idx, document.location(), page.src.replace("http://", "https://"))
        }
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
}
