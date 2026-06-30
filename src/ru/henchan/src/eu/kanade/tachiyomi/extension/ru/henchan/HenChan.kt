package eu.kanade.tachiyomi.extension.ru.henchan

import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HenChan : MultiChan() {

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/newest?offset=${20 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("do", "search")
                .addQueryParameter("subaction", "search")
                .addQueryParameter("story", query)
                .addQueryParameter("search_start", page.toString())
                .build()
                .toString()
            return GET(url, headers)
        }

        var genres = ""
        val filterList = filters.ifEmpty { getFilterList() }

        filterList.forEach { filter ->
            if (filter is GenreList) {
                filter.state
                    .filter { !it.isIgnored() }
                    .forEach { f ->
                        genres += (if (f.isExcluded()) "-" else "") + f.id + '+'
                    }
            }
        }

        val orderBy = filterList.firstInstanceOrNull<OrderBy>()
        val url = if (genres.isNotEmpty()) {
            val order = orderBy?.toUriPartWithGenres() ?: ""
            "$baseUrl/tags/${genres.dropLast(1)}&sort=manga$order?offset=${20 * (page - 1)}"
        } else {
            val order = orderBy?.toUriPartWithoutGenres() ?: ""
            "$baseUrl/$order?offset=${20 * (page - 1)}"
        }

        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".content_row:not(:has(div.item:containsOwn(Тип)))"

    private fun String.getHQThumbnail(): String {
        val isExHenManga = this.contains("/manganew_thumbs_blur/")
        val regex = manganewThumbsRegex
        return this.replace(regex, "showfull_retina/manga")
            .replace(
                "_".plus(URL(baseUrl).host),
                "_hentaichan.ru",
            ) // domain-related replacing for very old mangas
            .plus(
                if (isExHenManga) {
                    "#"
                } else {
                    ""
                },
            ) // # for later so we know what type manga is it
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = super.popularMangaFromElement(element)
        manga.thumbnail_url = element.selectFirst("img")?.attr("abs:src")?.getHQThumbnail()
        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)
        manga.thumbnail_url = document.selectFirst("img#cover")?.attr("abs:src")?.getHQThumbnail()
        return manga
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservable().doOnNext { response ->
            if (!response.isSuccessful) {
                response.close()
                // Error message for exceeding last page
                if (response.code == 404) {
                    Observable.just(
                        listOf(
                            SChapter.create().apply {
                                url = manga.url
                                name = "Chapter"
                                chapter_number = 1f
                            },
                        ),
                    )
                } else {
                    throw Exception("HTTP error ${response.code}")
                }
            }
        }
        .map { response ->
            chapterListParse(response)
        }

    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl + if (manga.thumbnail_url?.endsWith("#") == true) {
            manga.url
        } else {
            manga.url.replace("/manga/", "/related/")
        }
        return GET(url, headers)
    }

    override fun chapterListSelector() = ".related"

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseUrl = response.request.url.toString()
        val document = response.asJsoup()

        // exhentai chapter
        if (responseUrl.contains("/manga/")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(responseUrl)
            chap.name = document.select("a.title_top_a").text()
            chap.chapter_number = 1F

            val dateText = document.select("div.row4_right b").text()
            chap.date_upload = exhentaiDateFormat.tryParse(dateText)
            return listOf(chap)
        }

        // one chapter, nothing related
        val relatedText = document.select("#right > div:nth-child(4)").text()
        if (relatedText.contains(" похожий на ")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(document.selectFirst("#left > div > a")?.attr("abs:href") ?: "")
            chap.name = relatedText
                .split(" похожий на ")[1]
                .replace("\\\"", "\"")
                .replace("\\'", "'")
            chap.chapter_number = 1F
            return listOf(chap)
        }

        // has related chapters
        val result = mutableListOf<SChapter>()
        result.addAll(
            document.select(chapterListSelector()).map {
                chapterFromElement(it)
            },
        )

        var nextElement = document.selectFirst("div#pagination_related a:contains(Вперед)")
        while (nextElement != null) {
            val url = nextElement.attr("abs:href")
            if (url.isEmpty()) break

            val get = GET(url, headers = headers)
            val nextPage = client.newCall(get).execute().asJsoup()
            result.addAll(
                nextPage.select(chapterListSelector()).map {
                    chapterFromElement(it)
                },
            )

            nextElement = nextPage.selectFirst("div#pagination_related a:contains(Вперед)")
        }

        return result.reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val aElement = element.selectFirst("h2 a")
        chapter.setUrlWithoutDomain(aElement?.attr("abs:href") ?: "")
        val chapterName = aElement?.attr("title") ?: ""
        chapter.name = chapterName
        chapter.chapter_number = chapterNumberRegex.find(chapterName)?.groupValues?.get(2)?.toFloat() ?: -1F
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = if (chapter.url.contains("/manga/")) {
            baseUrl + chapter.url.replace("/manga/", "/online/")
        } else {
            baseUrl + chapter.url
        }
        return GET(url, Headers.Builder().add("Accept", "image/webp,image/apng").build())
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val prefix = "fullimg\": ["
        val beginIndex = html.indexOf(prefix) + prefix.length
        val endIndex = html.indexOf("]", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)
            .replace("\"", "")
            .replace("\'", "")

        val pageUrls = trimmedHtml.split(", ")
        return pageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun getFilterList() = FilterList(
        OrderBy(),
        GenreList(getGenreList()),
    )

    companion object {
        private val manganewThumbsRegex = "(?<=/)manganew_thumbs\\w*?(?=/)".toRegex(RegexOption.IGNORE_CASE)
        private val chapterNumberRegex = "(глава\\s|часть\\s)([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
        private val exhentaiDateFormat by lazy {
            SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
        }
    }
}
