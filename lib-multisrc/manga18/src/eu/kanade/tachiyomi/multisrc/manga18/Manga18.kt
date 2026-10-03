package eu.kanade.tachiyomi.multisrc.manga18

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class Manga18 : KeiSource() {

    override suspend fun getPopularManga(page: Int) = popularMangaParse(client.get("$baseUrl/list-manga/$page?order_by=views").asJsoup())

    protected open fun popularMangaParse(document: Document): MangasPage {
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

    override suspend fun getLatestUpdates(page: Int) = popularMangaParse(client.get("$baseUrl/list-manga/$page").asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return popularMangaParse(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manhwa") return null

        return mangaDetailsParse(client.get(url).asJsoup()).apply { setUrlWithoutDomain(url.toString()) }
    }

    protected open val getAvailableTags = true
    protected open val tagsSelector = "div.grid_cate li > a"

    override val supportsFilterFetching get() = getAvailableTags

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/list-manga/1").asJsoup()
        val tags = document.select(tagsSelector).map {
            Pair(
                it.text(),
                it.attr("href")
                    .removeSuffix("/")
                    .substringAfterLast("/"),
            )
        }
        return (listOf(Pair("", "")) + tags).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<Pair<String, String>>>() ?: return FilterList()

        return FilterList(
            Filter.Header("Ignored with text search"),
            Filter.Separator(),
            SortFilter(),
            TagFilter(tags),
        )
    }

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), document.select(chapterListSelector()).map(::chapterFromElement))
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

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
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

    protected open fun chapterListSelector() = "div.chapter_box .item"

    protected open val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)

    protected open fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(absUrl("href"))
            name = text()
        }
        date_upload = dateFormat.tryParseDate(element.selectFirst("p")?.text())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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
}
