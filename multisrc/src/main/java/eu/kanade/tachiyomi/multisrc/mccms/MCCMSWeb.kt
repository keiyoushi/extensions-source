package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator
import rx.Observable

// https://github.com/tachiyomiorg/tachiyomi-extensions/blob/e0b4fcbce8aa87742da22e7fa60b834313f53533/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/mccms/MCCMS.kt
open class MCCMSWeb(
    name: String,
    baseUrl: String,
    lang: String = "zh",
    hasCategoryPage: Boolean = true,
) : MCCMS(name, baseUrl, lang, hasCategoryPage) {

    protected open fun parseListing(document: Document): MangasPage {
        val mangas = document.select(Evaluator.Class("common-comic-item")).map {
            SManga.create().apply {
                val titleElement = it.selectFirst(Evaluator.Class("comic__title"))!!.child(0)
                url = titleElement.attr("href")
                title = titleElement.ownText()
                thumbnail_url = it.selectFirst(Evaluator.Tag("img"))!!.attr("data-original")
            }.cleanup()
        }
        val hasNextPage = run { // default pagination
            val buttons = document.selectFirst(Evaluator.Id("Pagination"))!!.select(Evaluator.Tag("a"))
            val count = buttons.size
            // Next page != Last page
            buttons[count - 1].attr("href") != buttons[count - 2].attr("href")
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/order/hits/page/$page", pcHeaders)

    override fun popularMangaParse(response: Response) = parseListing(response.asJsoup())

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/order/addtime/page/$page", pcHeaders)

    override fun latestUpdatesParse(response: Response) = parseListing(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotBlank()) {
            val url = "$baseUrl/index.php/search".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .toString()
            GET(url, pcHeaders)
        } else {
            val url = buildString {
                append(baseUrl).append("/category/")
                filters.filterIsInstance<MCCMSFilter>().map { it.query }.filter { it.isNotEmpty() }
                    .joinTo(this, "/")
                append("/page/").append(page)
            }
            GET(url, pcHeaders)
        }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (document.selectFirst(Evaluator.Id("code-div")) != null) {
            val manga = SManga.create().apply {
                url = "/index.php/search"
                title = "验证码"
                description = "请点击 WebView 按钮输入验证码，完成后返回重新搜索"
                initialized = true
            }
            return MangasPage(listOf(manga), false)
        }
        val result = parseListing(document)
        if (document.location().contains("search")) {
            return MangasPage(result.mangas, false)
        }
        return result
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        if (manga.url == "/index.php/search") return Observable.just(manga)
        return client.newCall(GET(baseUrl + manga.url, pcHeaders)).asObservableSuccess().map { response ->
            mangaDetailsParse(response)
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return run {
            SManga.create().apply {
                val document = response.asJsoup().selectFirst(Evaluator.Class("de-info__box"))!!
                title = document.selectFirst(Evaluator.Class("comic-title"))!!.ownText()
                thumbnail_url = document.selectFirst(Evaluator.Tag("img"))!!.attr("src")
                author = document.selectFirst(Evaluator.Class("name"))!!.text()
                genre = document.selectFirst(Evaluator.Class("comic-status"))!!.select(Evaluator.Tag("a")).joinToString { it.ownText() }
                description = document.selectFirst(Evaluator.Class("intro-total"))!!.text()
            }.cleanup()
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (manga.url == "/index.php/search") return Observable.just(emptyList())
        return client.newCall(GET(baseUrl + manga.url, pcHeaders)).asObservableSuccess().map { response ->
            chapterListParse(response)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return run {
            response.asJsoup().selectFirst(Evaluator.Class("chapter__list-box"))!!.children().map {
                val link = it.child(0)
                SChapter.create().apply {
                    url = link.attr("href")
                    name = link.ownText()
                }
            }.asReversed()
        }
    }

    override fun getFilterList(): FilterList {
        fetchGenres()
        return getWebFilters(genreData)
    }
}
