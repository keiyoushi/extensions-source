package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
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
) : MangaReader() {
    override val name = "MangaFire"

    override val baseUrl = "https://mangafire.to"

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor)
        .build()

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/filter?sort=recently_updated&language[]=$langCode&page=$page", headers)

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/filter?sort=most_viewed&language[]=$langCode&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("filter").apply {
                addQueryParameter("keyword", query)
                addQueryParameter("page", page.toString())
            }
        } else {
            urlBuilder.addPathSegment("filter").apply {
                addQueryParameter("language[]", langCode)
                addQueryParameter("page", page.toString())
                filters.ifEmpty(::getFilterList).forEach { filter ->
                    when (filter) {
                        is Group -> {
                            filter.state.forEach {
                                if (it.state) {
                                    addQueryParameter(filter.param, it.id)
                                }
                            }
                        }
                        is Select -> {
                            addQueryParameter(filter.param, filter.selection)
                        }
                        is GenresFilter -> {
                            filter.state.forEach {
                                if (it.state != 0) {
                                    addQueryParameter(filter.param, it.selection)
                                }
                            }
                            if (filter.combineMode) {
                                addQueryParameter("genre_mode", "and")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaNextPageSelector() = ".page-item.active + .page-item .page-link"

    override fun searchMangaSelector() = ".original.card-lg .unit .inner"

    override fun searchMangaFromElement(element: Element) =
        SManga.create().apply {
            element.selectFirst(".info > a")!!.let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.ownText()
            }
            element.selectFirst(Evaluator.Tag("img"))!!.let {
                thumbnail_url = it.attr("src")
            }
        }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val root = document.selectFirst(".info")!!
        val mangaTitle = root.child(1).ownText()
        title = mangaTitle
        description = document.run {
            val description = selectFirst(Evaluator.Class("description"))!!.ownText()
            when (val altTitle = root.child(2).ownText()) {
                "", mangaTitle -> description
                else -> "$description\n\nAlternative Title: $altTitle"
            }
        }
        thumbnail_url = document.selectFirst(".poster")!!
            .selectFirst("img")!!.attr("src")
        status = when (root.child(0).ownText()) {
            "Completed" -> SManga.COMPLETED
            "Releasing" -> SManga.ONGOING
            "On_hiatus" -> SManga.ON_HIATUS
            "Discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        with(document.selectFirst(Evaluator.Class("meta"))!!) {
            author = selectFirst("span:contains(Author:) + span")?.text()
            val type = selectFirst("span:contains(Type:) + span")?.text()
            val genres = selectFirst("span:contains(Genres:) + span")?.text()
            genre = listOfNotNull(type, genres).joinToString()
        }
    }

    override val chapterType get() = "chapter"
    override val volumeType get() = "volume"

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

    override fun getFilterList() =
        FilterList(
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
            TypeFilter(),
            GenresFilter(),
            StatusFilter(),
            YearFilter(),
            ChapterCountFilter(),
            SortFilter(),
        )
}
