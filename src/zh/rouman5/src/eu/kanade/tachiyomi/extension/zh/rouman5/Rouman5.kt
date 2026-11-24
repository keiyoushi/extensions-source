package eu.kanade.tachiyomi.extension.zh.rouman5

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Rouman5 : KeiSource() {

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ScrambledImageInterceptor())

    private fun getItems(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select("li.hl-list-item")
        val manga = elements.map {
            SManga.create().apply {
                title = it.selectFirst("a")!!.attr("title")
                thumbnail_url = it.selectFirst("a")!!.attr("data-original")
                url = it.selectFirst("a")!!.attr("href")
            }
        }
        return MangasPage(
            mangas = manga,
            hasNextPage = elements.isNotEmpty(),
        )
    }
    override suspend fun getPopularManga(page: Int): MangasPage {
        // 2. 手动执行网络请求（使用 suspend 扩展函数）
        val request = GET("$baseUrl/bookcatalog/all/ob/hits/st/all/page/$page", headers)
        val response = request.execute() // 或 client.newCall(request).execute()

        return getItems(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = GET("$baseUrl/bookcatalog/all/ob/time/st/all/page/$page", headers).execute()
        return getItems(response)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = GET("$baseUrl/cata.php?key=$query", headers).execute()
        return getItems(response)
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        // 如果不需要增量更新，直接返回默认值
        return SMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
    }

//    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
//        val document = response.asJsoup()
//        title = document.selectFirst("h1.hl-dc-title")!!.text()
//        thumbnail_url = document.selectFirst("div.hl-dc-pic>span")!!.attr("data-original")
//        description = document.selectFirst("div.hl-data-xs>span:nth-of-type(4)")!!.text()
//        author = document.selectFirst("div.hl-data-xs>span:nth-of-type(2)")!!.text()
//    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("a.module-play-list-link").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.text()
            }
        }
        return chapters.asReversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // 1. 请求章节的URL
        val response = GET("$baseUrl${chapter.url}", headers).execute()

        // 2. 解析HTML
        val document = response.asJsoup()

        val images = document.select(".img-wrap img").map { it.attr("data-src") }

        return images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("提示：搜尋時篩選無效"),
        StatusFilter(),
    )

    private abstract class UriPartFilter(name: String, values: Array<String>) : Filter.Select<String>(name, values) {
        abstract fun toUriPart(): String
    }

    private class StatusFilter : UriPartFilter("狀態", arrayOf("全部", "連載中", "已完結")) {
        override fun toUriPart() = when (state) {
            1 -> "&continued=true"
            2 -> "&continued=false"
            else -> ""
        }
    }
}
