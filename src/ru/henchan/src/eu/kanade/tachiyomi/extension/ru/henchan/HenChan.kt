package eu.kanade.tachiyomi.extension.ru.henchan

import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HenChan : MultiChan() {

    override val siteDomains = listOf("hentaichan.live", "h-chan.me")

    override fun popularMangaUrl(page: Int) = "$baseUrl/manga/newest&n=favdesc?offset=${20 * (page - 1)}"

    override fun latestUpdatesUrl(page: Int) = "$baseUrl/manga/newest?offset=${20 * (page - 1)}"

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList): String {
        if (query.isNotEmpty()) {
            return baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("do", "search")
                .addQueryParameter("subaction", "search")
                .addQueryParameter("story", query)
                .addQueryParameter("search_start", page.toString())
                .build()
                .toString()
        }

        val genres = filters.firstInstanceOrNull<GenreList>()?.state.orEmpty()
            .filterNot { it.isIgnored() }
            .joinToString("+") { (if (it.isExcluded()) "-" else "") + it.id }

        val orderBy = filters.firstInstanceOrNull<OrderBy>()
        return if (genres.isNotEmpty()) {
            val order = orderBy?.toUriPartWithGenres() ?: ""
            "$baseUrl/tags/$genres&sort=manga$order?offset=${20 * (page - 1)}"
        } else {
            val order = orderBy?.toUriPartWithoutGenres() ?: "manga/newest"
            "$baseUrl/$order?offset=${20 * (page - 1)}"
        }
    }

    override fun searchMangaSelector() = ".content_row:not(:has(div.item:containsOwn(Тип)))"

    private fun String.getHQThumbnail(): String {
        val isExHenManga = this.contains("/manganew_thumbs_blur/")
        return this.replace(manganewThumbsRegex, "showfull_retina/manga")
            .replace("_" + baseUrl.toHttpUrl().host, "_hentaichan.ru") // domain-related replacing for very old mangas
            .plus(if (isExHenManga) "#" else "") // # for later so we know what type manga is it
    }

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).apply {
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")?.getHQThumbnail()
    }

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        thumbnail_url = document.selectFirst("img#cover")?.attr("abs:src")?.getHQThumbnail()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val isExHentai = manga.thumbnail_url?.endsWith("#") == true
        val related = if (fetchChapters && !isExHentai) {
            async { client.get(sectionUrl("related", manga.url), Fetch.DOCUMENT, ensureSuccess = false) }
        } else {
            null
        }
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val relatedResponse = related?.await()
        val chapterList = when {
            isExHentai -> chapterListParse(document, document)
            relatedResponse == null -> chapters
            relatedResponse.isSuccessful -> chapterListParse(document, relatedResponse.asJsoup())
            // Error message for exceeding last page
            relatedResponse.code == 404 -> {
                relatedResponse.close()
                listOf(
                    SChapter.create().apply {
                        url = manga.url
                        name = "Chapter"
                        chapter_number = 1f
                    },
                )
            }
            else -> {
                relatedResponse.close()
                throw HttpException(relatedResponse.code)
            }
        }
        SMangaUpdate(mangaDetailsParse(document), chapterList)
    }

    override fun chapterListSelector() = ".related"

    private suspend fun chapterListParse(mangaPage: Document, relatedPage: Document): List<SChapter> {
        val responseUrl = relatedPage.location()
        val uploadDate = exhentaiDateFormat.tryParseDate(mangaPage.selectFirst("div.row4_right b")?.text())

        // exhentai chapter
        if (responseUrl.contains("/manga/")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(responseUrl)
            chap.name = relatedPage.selectFirst("a.title_top_a")!!.text()
            chap.chapter_number = 1F
            chap.date_upload = uploadDate
            return listOf(chap)
        }

        // one chapter, nothing related
        val relatedText = relatedPage.selectFirst("#right > div:containsOwn(похожий на)")?.text()
        if (relatedText != null) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(relatedPage.selectFirst("#left > div > a")!!.attr("abs:href"))
            chap.name = relatedText
                .substringAfter(" похожий на ")
                .replace("\\\"", "\"")
                .replace("\'", "'")
            chap.chapter_number = 1F
            chap.date_upload = uploadDate
            return listOf(chap)
        }

        // has related chapters
        val result = relatedPage.select(chapterListSelector()).map(::chapterFromElement).toMutableList()

        var nextElement = relatedPage.selectFirst("div#pagination_related a:contains(Вперед)")
        while (nextElement != null) {
            val url = nextElement.attr("abs:href")
            if (url.isEmpty()) break

            val nextPage = client.get(url, Fetch.DOCUMENT).asJsoup()
            result.addAll(nextPage.select(chapterListSelector()).map(::chapterFromElement))

            nextElement = nextPage.selectFirst("div#pagination_related a:contains(Вперед)")
        }

        return result.reversed()
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val aElement = element.selectFirst("h2 a")!!
        setUrlWithoutDomain(aElement.attr("abs:href"))
        name = aElement.attrOrNull("title")!!
        chapter_number = chapterNumberRegex.find(name)?.groupValues?.get(2)?.toFloat() ?: -1F
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = sectionUrl("online", chapter.url)
        return pageListParse(client.get(url, Fetch.DOCUMENT).body.string())
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

    // The same gallery under another section: /related/, /online/
    private fun sectionUrl(section: String, path: String) = (baseUrl + path).toHttpUrl().newBuilder().setPathSegment(0, section).build()

    companion object {
        private val manganewThumbsRegex = """(?<=/)manganew_thumbs\w*?(?=/)""".toRegex(RegexOption.IGNORE_CASE)
        private val exhentaiDateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ru"))
    }
}
