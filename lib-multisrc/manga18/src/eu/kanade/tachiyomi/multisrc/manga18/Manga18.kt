package eu.kanade.tachiyomi.multisrc.manga18

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

abstract class Manga18 : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/list-manga/$page?order_by=views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        getTags(document)

        val entries = document.select(popularMangaSelector()).map(::popularMangaFromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null

        return MangasPage(entries, hasNextPage)
    }

    protected open fun popularMangaSelector() = "div.story_item"
    protected open fun popularMangaNextPageSelector() = ".pagination > li:last-child:not(.active)"

    protected open fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("div.mg_info > div.mg_name a")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list-manga/$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val tag = filters.firstInstanceOrNull<TagFilter>()
            if (query.isNotEmpty() || tag?.selected.isNullOrEmpty()) {
                addPathSegment("list-manga")
                addPathSegment(page.toString())
                addQueryParameter("search", query.trim())
            } else {
                addPathSegment("manga-list")
                addPathSegment(tag!!.selected!!)
                addPathSegment(page.toString())
                filters.firstInstanceOrNull<SortFilter>()?.selected?.let {
                    addQueryParameter("order_by", it)
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    protected open val getAvailableTags = true
    protected open val tagsSelector = "div.grid_cate li > a"

    private var tags = emptyList<Pair<String, String>>()
    private var getTagsAttempts = 0

    protected open fun getTags(document: Document) {
        if (getAvailableTags && tags.isEmpty() && getTagsAttempts < 3) {
            try {
                tags = document.select(tagsSelector).map {
                    Pair(
                        it.text(),
                        it.attr("href")
                            .removeSuffix("/")
                            .substringAfterLast("/"),
                    )
                }.let {
                    listOf(Pair("", "")) + it
                }
            } catch (e: Exception) {
                Log.d(name, e.stackTraceToString())
            } finally {
                getTagsAttempts++
            }
        }
    }

    override fun getFilterList(): FilterList {
        if (!getAvailableTags) return FilterList()

        return if (tags.isEmpty()) {
            FilterList(
                Filter.Header("Press 'reset' to attempt to load genres"),
            )
        } else {
            FilterList(
                Filter.Header("Ignored with text search"),
                Filter.Separator(),
                SortFilter(),
                TagFilter(tags),
            )
        }
    }

    protected open val infoElementSelector = "div.detail_listInfo"
    protected open val titleSelector = "div.detail_name > h1"
    protected open val descriptionSelector = "div.detail_reviewContent"
    protected open val statusSelector = "div.item:contains(Status) div.info_value"
    protected open val altNameSelector = "div.item:contains(Other name) div.info_value"
    protected open val genreSelector = "div.info_value > a[href*=/manga-list/]"
    protected open val authorSelector = "div.info_label:contains(author) + div.info_value, div.info_label:contains(autor) + div.info_value"
    protected open val artistSelector = "div.info_label:contains(artist) + div.info_value"
    protected open val thumbnailSelector = "div.detail_avatar > img"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val info = document.selectFirst(infoElementSelector)!!

            title = document.select(titleSelector).text()
            description = buildString {
                document.select(descriptionSelector)
                    .eachText().onEach {
                        append(it)
                        append("\n\n")
                    }

                info.selectFirst(altNameSelector)
                    ?.text()
                    ?.takeIf { it != "Updating" && it.isNotEmpty() }
                    ?.let {
                        append("Alternative Names:\n")
                        append(it)
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
    }

    protected open fun chapterListSelector() = "div.chapter_box .item"

    protected open val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map(::chapterFromElement)
    }

    protected open fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(absUrl("href"))
            name = text()
        }
        date_upload = dateFormat.tryParse(element.selectFirst("p")?.text())
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
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

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
