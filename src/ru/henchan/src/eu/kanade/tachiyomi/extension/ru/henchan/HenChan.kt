package eu.kanade.tachiyomi.extension.ru.henchan

import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HenChan : MultiChan() {

    override fun latestUpdatesUrl(page: Int) = "$baseUrl/manga/newest?offset=${20 * (page - 1)}"

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList): String {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("do", "search")
                .addQueryParameter("subaction", "search")
                .addQueryParameter("story", query)
                .addQueryParameter("search_start", page.toString())
                .build()
                .toString()
            return url
        }

        var genres = ""

        filters.forEach { filter ->
            if (filter is GenreList) {
                filter.state
                    .filter { !it.isIgnored() }
                    .forEach { f ->
                        genres += (if (f.isExcluded()) "-" else "") + f.id + '+'
                    }
            }
        }

        val orderBy = filters.firstInstanceOrNull<OrderBy>()
        val url = if (genres.isNotEmpty()) {
            val order = orderBy?.toUriPartWithGenres() ?: ""
            "$baseUrl/tags/${genres.dropLast(1)}&sort=manga$order?offset=${20 * (page - 1)}"
        } else {
            val order = orderBy?.toUriPartWithoutGenres() ?: ""
            "$baseUrl/$order?offset=${20 * (page - 1)}"
        }

        return url
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

    override suspend fun fetchChapterList(manga: SManga, mangaPage: Document): List<SChapter> {
        if (manga.thumbnail_url?.endsWith("#") == true) {
            return chapterListParse(mangaPage)
        }

        val response = client.get(baseUrl + manga.url.replace("/manga/", "/related/"), ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            // Error message for exceeding last page
            if (response.code == 404) {
                return listOf(
                    SChapter.create().apply {
                        url = manga.url
                        name = "Chapter"
                        chapter_number = 1f
                    },
                )
            }
            throw HttpException(response.code)
        }
        return chapterListParse(response.asJsoup())
    }

    override fun chapterListSelector() = ".related"

    private suspend fun chapterListParse(document: Document): List<SChapter> {
        val responseUrl = document.location()

        // exhentai chapter
        if (responseUrl.contains("/manga/")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(responseUrl)
            chap.name = document.select("a.title_top_a").text()
            chap.chapter_number = 1F

            val dateText = document.select("div.row4_right b").text()
            chap.date_upload = exhentaiDateFormat.tryParseDate(dateText)
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
                .replace("\'", "'")
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

            val nextPage = client.get(url).asJsoup()
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = if (chapter.url.contains("/manga/")) {
            baseUrl + chapter.url.replace("/manga/", "/online/")
        } else {
            baseUrl + chapter.url
        }
        return pageListParse(client.get(url, headersBuilder().add("Accept", "image/webp,image/apng").build()).body.string())
    }

    override fun pageListParse(html: String): List<Page> {
        val prefix = "fullimg\": ["
        val beginIndex = html.indexOf(prefix) + prefix.length
        val endIndex = html.indexOf("]", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)
            .replace("\"", "")
            .replace("\'", "")

        val pageUrls = trimmedHtml.split(", ")
        return pageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderBy(),
        GenreList(getGenreList()),
    )

    companion object {
        private val manganewThumbsRegex = "(?<=/)manganew_thumbs\\w*?(?=/)".toRegex(RegexOption.IGNORE_CASE)
        private val chapterNumberRegex = "(глава\\s|часть\\s)([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
        private val exhentaiDateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ru"))
    }
}
