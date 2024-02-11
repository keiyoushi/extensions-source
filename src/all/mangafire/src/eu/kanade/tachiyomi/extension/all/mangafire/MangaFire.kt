package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

open class MangaFire(
    override val lang: String,
    private val langCode: String = lang,
) : MangaReader(
    "MangaFire",
    "https://mangafire.to",
    lang,
    "most_viewed",
    "recently_updated",
) {

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor)
        .build()

    override val pageQueryParameter = "page"

    override val containsVolumes = true
    override val chapterType = "chapter"
    override val volumeType = "volume"

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val filterList = filters.ifEmpty { getFilterList() }

            addPathSegment("filter")
            addQueryParameter(searchKeyword, query)
            addQueryParameter("language[]", langCode)
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }

            addQueryParameter(pageQueryParameter, page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".original.card-lg .unit .inner"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".info > a")!!.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.ownText()
        }
        element.selectFirst("img")!!.let {
            thumbnail_url = it.imgAttr()
        }
    }

    override fun searchMangaNextPageSelector() = ".page-item.active + .page-item .page-link"

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val rootSelector = ".main-inner:has(.meta)"

        document.selectFirst(rootSelector)!!.run {
            title = selectFirst("h1")!!.ownText()
            thumbnail_url = selectFirst("img")?.imgAttr()
            genre = select(".meta > div:has(span:contains(Genres)) a").joinToString { it.ownText() }
            author = select(".meta > div:has(span:contains(Author)) a").joinToString { it.ownText() }
            status = selectFirst(".info > p")?.text().getStatus()
        }

        description = buildString {
            document.selectFirst("#synopsis")?.text()?.let { append(it) }
            append("\n\n")
            document.selectFirst("$rootSelector h6")?.ownText()?.let {
                if (it.isNotEmpty() && it != title) {
                    append("Alternative Title: ")
                    append(it)
                }
            }
        }.trim()
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(mangaUrl: String, type: String): Request {
        val id = mangaUrl.substringAfterLast('.')
        return GET("$baseUrl/ajax/manga/$id/$type/$langCode", headers)
    }

    override fun parseChapterElements(response: Response, isVolume: Boolean): List<Element> {
        val result = json.decodeFromString<ResponseDto<String>>(response.body.string()).result
        val document = Jsoup.parse(result)

        val elements = document.select("ul li")
        if (elements.size > 0) {
            val linkToFirstChapter = elements[0].selectFirst(Evaluator.Tag("a"))!!.attr("href")
            val mangaId = linkToFirstChapter.toString().substringAfter('.').substringBefore('/')

            val request = GET("$baseUrl/ajax/read/$mangaId/chapter/$langCode", headers)
            val response = client.newCall(request).execute()
            val res = json.decodeFromString<ResponseDto<ChapterIdsDto>>(response.body.string()).result.html
            val chapterInfoDocument = Jsoup.parse(res)
            val chapters = chapterInfoDocument.select("ul li")
            for ((i, it) in elements.withIndex()) {
                it.attr("data-id", chapters[i].select("a").attr("data-id"))
            }
        }
        return elements.toList()
    }

    @Serializable
    class ChapterIdsDto(
        val html: String,
        val title_format: String,
    )

    override fun updateChapterList(manga: SManga, chapters: List<SChapter>) {
        val request = chapterListRequest(manga.url, chapterType)
        val response = client.newCall(request).execute()
        val result = json.decodeFromString<ResponseDto<String>>(response.body.string()).result
        val document = Jsoup.parse(result)

        val elements = document.selectFirst(".scroll-sm")!!.children()
        val chapterCount = chapters.size

        if (elements.size != chapterCount) throw Exception("Chapter count doesn't match. Try updating again.")
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        for (i in 0 until chapterCount) {
            val chapter = chapters[i]
            val element = elements[i]

            val number = element.attr("data-number").toFloatOrNull() ?: -1f

            if (chapter.chapter_number != number) throw Exception("Chapter number doesn't match. Try updating again.")
            val date = element.select(Evaluator.Tag("span"))[1].ownText()

            chapter.date_upload = try {
                dateFormat.parse(date)!!.time
            } catch (_: Throwable) {
                0
            }
        }
    }

    override fun chapterFromElement(element: Element, isVolume: Boolean): SChapter = SChapter.create().apply {
        val number = element.attr("data-number")
        chapter_number = number.toFloatOrNull() ?: -1f

        val link = element.selectFirst("a")!!
        val abbrPrefix = if (isVolume) "Vol" else "Chap"
        val fullPrefix = if (isVolume) "Volume" else "Chapter"
        val type = if (isVolume) volumeType else chapterType
        name = run {
            val name = link.selectFirst("span")?.text() ?: link.text()
            val prefix = "$abbrPrefix $number: "
            if (!name.startsWith(prefix)) return@run name
            val realName = name.removePrefix(prefix)
            if (realName.contains(number)) realName else "$fullPrefix $number: $realName"
        }
        setUrlWithoutDomain(link.attr("href") + '#' + type + '/' + element.attr("data-id"))
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val typeAndId = chapter.url.substringAfterLast('#')
        return GET("$baseUrl/ajax/read/$typeAndId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<ResponseDto<PageListDto>>(response.body.string()).result

        return result.pages.mapIndexed { index, image ->
            val url = image.url
            val offset = image.offset
            val imageUrl = if (offset > 0) "$url#${ImageInterceptor.SCRAMBLED}_$offset" else url

            Page(index, imageUrl = imageUrl)
        }
    }

    @Serializable
    class PageListDto(private val images: List<List<JsonPrimitive>>) {
        val pages get() = images.map {
            Image(it[0].content, it[2].int)
        }
    }
    class Image(val url: String, val offset: Int)

    @Serializable
    class ResponseDto<T>(
        val result: T,
        val status: Int,
    )

    // =============================== Filters ==============================

    override fun getSortFilter() = SortFilter(sortFilterName, sortFilterParam, sortFilterValues)

    override val sortFilterValues = arrayOf(
        Pair("Most relevance", "most_relevance"),
        Pair("Trending", "trending"),
        Pair("Recently updated", "recently_updated"),
        Pair("Recently added", "recently_added"),
        Pair("Release date", "release_date"),
        Pair("Name A-Z", "title_az"),
        Pair("Score", "scores"),
        Pair("MAL score", "mal_scores"),
        Pair("Most viewed", "most_viewed"),
        Pair("Most favourited", "most_favourited"),
    )

    override fun getFilterList() = FilterList(
        TypeFilter(),
        GenreFilter(),
        GenreModeFilter(),
        StatusFilter(),
        YearFilter(),
        ChapterCountFilter(),
        getSortFilter(),
    )
}
