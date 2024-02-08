package eu.kanade.tachiyomi.multisrc.manga18

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.Exception
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class Manga18(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/list-manga/$page?order_by=views", headers)
    }

    override fun popularMangaSelector() = "div.story_item"
    override fun popularMangaNextPageSelector() = ".pagination a[rel=next]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("div.mg_info > div.mg_name a")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/list-manga/$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/list-manga/$page".toHttpUrl().newBuilder()
            .addQueryParameter("search", query.trim())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    protected open val infoElementSelector = "div.detail_listInfo"
    protected open val titleSelector = "div.detail_name > h1"
    protected open val descriptionSelector = "div.detail_reviewContent"
    protected open val statusSelector = "div.item:contains(Status) div.info_value"
    protected open val altNameSelector = "div.item:contains(Other name) div.info_value"
    protected open val genreSelector = "div.info_value > a[href*=/manga-list/]"
    protected open val authorSelector = "div.info_label:contains(author) + div.info_value, div.info_label:contains(autor) + div.info_value"
    protected open val artistSelector = "div.info_label:contains(artist) + div.info_value"
    protected open val thumbnailSelector = "div.detail_avatar > img"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val info = document.selectFirst(infoElementSelector)!!

        title = document.select(titleSelector).text()
        description = buildString {
            document.select(descriptionSelector)
                .eachText().onEach {
                    append(it.trim())
                    append("\n\n")
                }

            info.selectFirst(altNameSelector)
                ?.text()
                ?.takeIf { it != "Updating" || it.isNotEmpty() }
                ?.let {
                    append("Alternative Names:\n")
                    append(it.trim())
                }
        }
        status = when (info.select(statusSelector).text()) {
            "On Going" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = info.selectFirst(authorSelector)?.text()?.takeIf { it != "Updating" }
        artist = info.selectFirst(artistSelector)?.text()?.takeIf { it != "Updating" }
        genre = info.select(genreSelector).eachText().joinToString()
        thumbnail_url = document.selectFirst(thumbnailSelector)?.absUrl("src")
    }


    override fun chapterListSelector() = "div.chapter_box .item"

    protected open val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(absUrl("href"))
            name = text()
        }
        date_upload = try {
            dateFormat.parse(element.selectFirst("p")!!.text())!!.time
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(slides_p_path)")
            ?: throw Exception("Unable to find script with image data")

        val encodedImages = script.data()
            .substringAfter('[')
            .substringBefore(",]")
            .replace("\"", "")
            .split(",")

        return encodedImages.mapIndexed { idx, encoded ->
            val url = Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
            val imageUrl = when {
                url.startsWith("/") -> "$baseUrl$url"
                else -> url
            }
            Page(idx, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
