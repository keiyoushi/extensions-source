package eu.kanade.tachiyomi.extension.all.mangareaderto

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Evaluator
import rx.Observable

open class MangaReader(
    override val lang: String,
) : MangaReader() {
    override val name = "MangaReader"

    override val baseUrl = "https://mangareader.to"

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor)
        .build()

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/filter?sort=latest-updated&language=$lang&page=$page", headers)

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/filter?sort=most-viewed&language=$lang&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("search").apply {
                addQueryParameter("keyword", query)
                addQueryParameter("page", page.toString())
            }
        } else {
            urlBuilder.addPathSegment("filter").apply {
                addQueryParameter("language", lang)
                addQueryParameter("page", page.toString())
                filters.ifEmpty(::getFilterList).forEach { filter ->
                    when (filter) {
                        is Select -> {
                            addQueryParameter(filter.param, filter.selection)
                        }
                        is DateFilter -> {
                            filter.state.forEach {
                                addQueryParameter(it.param, it.selection)
                            }
                        }
                        is GenresFilter -> {
                            addQueryParameter(filter.param, filter.selection)
                        }
                        else -> {}
                    }
                }
            }
        }
        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaSelector() = ".manga_list-sbs .manga-poster"

    override fun searchMangaNextPageSelector() = ".page-link[title=Next]"

    override fun searchMangaFromElement(element: Element) =
        SManga.create().apply {
            url = element.attr("href")
            element.selectFirst(Evaluator.Tag("img"))!!.let {
                title = it.attr("alt")
                thumbnail_url = it.attr("src")
            }
        }

    private fun Element.parseAuthorsTo(manga: SManga) {
        val authors = select(Evaluator.Tag("a"))
        val text = authors.map { it.ownText().replace(",", "") }
        val count = authors.size
        when (count) {
            0 -> return
            1 -> {
                manga.author = text[0]
                return
            }
        }
        val authorList = ArrayList<String>(count)
        val artistList = ArrayList<String>(count)
        for ((index, author) in authors.withIndex()) {
            val textNode = author.nextSibling() as? TextNode
            val list = if (textNode != null && "(Art)" in textNode.wholeText) artistList else authorList
            list.add(text[index])
        }
        if (authorList.isEmpty().not()) manga.author = authorList.joinToString()
        if (artistList.isEmpty().not()) manga.artist = artistList.joinToString()
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val root = document.selectFirst(Evaluator.Id("ani_detail"))!!
        val mangaTitle = root.selectFirst(Evaluator.Tag("h2"))!!.ownText()
        title = mangaTitle
        description = root.run {
            val description = selectFirst(Evaluator.Class("description"))!!.ownText()
            when (val altTitle = selectFirst(Evaluator.Class("manga-name-or"))!!.ownText()) {
                "", mangaTitle -> description
                else -> "$description\n\nAlternative Title: $altTitle"
            }
        }
        thumbnail_url = root.selectFirst(Evaluator.Tag("img"))!!.attr("src")
        genre = root.selectFirst(Evaluator.Class("genres"))!!.children().joinToString { it.ownText() }
        for (item in root.selectFirst(Evaluator.Class("anisc-info"))!!.children()) {
            if (item.hasClass("item").not()) continue
            when (item.selectFirst(Evaluator.Class("item-head"))!!.ownText()) {
                "Authors:" -> item.parseAuthorsTo(this)
                "Status:" -> status = when (item.selectFirst(Evaluator.Class("name"))!!.ownText()) {
                    "Finished" -> SManga.COMPLETED
                    "Publishing" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    override val chapterType get() = "chap"
    override val volumeType get() = "vol"

    override fun chapterListRequest(mangaUrl: String, type: String): Request {
        val id = mangaUrl.substringAfterLast('-')
        return GET("$baseUrl/ajax/manga/reading-list/$id?readingBy=$type", headers)
    }

    override fun parseChapterElements(response: Response, isVolume: Boolean): List<Element> {
        val container = response.parseHtmlProperty().run {
            val type = if (isVolume) "volumes" else "chapters"
            selectFirst(Evaluator.Id("$lang-$type")) ?: return emptyList()
        }
        return container.children()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val typeAndId = chapter.url.substringAfterLast('#', "").ifEmpty {
            val document = client.newCall(pageListRequest(chapter)).execute().asJsoup()
            val wrapper = document.selectFirst(Evaluator.Id("wrapper"))!!
            wrapper.attr("data-reading-by") + '/' + wrapper.attr("data-reading-id")
        }
        val ajaxUrl = "$baseUrl/ajax/image/list/$typeAndId?quality=${preferences.quality}"
        client.newCall(GET(ajaxUrl, headers)).execute().let(::pageListParse)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageDocument = response.parseHtmlProperty()

        return pageDocument.getElementsByClass("iv-card").mapIndexed { index, img ->
            val url = img.attr("data-url")
            val imageUrl = if (img.hasClass("shuffled")) "$url#${ImageInterceptor.SCRAMBLED}" else url
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferences(screen.context).forEach(screen::addPreference)
        super.setupPreferenceScreen(screen)
    }

    override fun getFilterList() =
        FilterList(
            Note,
            TypeFilter(),
            StatusFilter(),
            RatingFilter(),
            ScoreFilter(),
            StartDateFilter(),
            EndDateFilter(),
            SortFilter(),
            GenresFilter(),
        )

    private fun Response.parseHtmlProperty(): Document {
        val html = Json.parseToJsonElement(body.string()).jsonObject["html"]!!.jsonPrimitive.content
        return Jsoup.parseBodyFragment(html)
    }
}
