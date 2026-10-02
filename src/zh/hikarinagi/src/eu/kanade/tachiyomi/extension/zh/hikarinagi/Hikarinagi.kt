package eu.kanade.tachiyomi.extension.zh.hikarinagi

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getArray
import keiyoushi.utils.getInt
import keiyoushi.utils.getLong
import keiyoushi.utils.getObject
import keiyoushi.utils.getString
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Hikarinagi : KeiSource() {

    override fun getHomeUrl() = "$baseUrl/mangas"

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    companion object {
        const val IMAGE_BASR_URL = "https://imagesp.yurari.moe"
        val FILTER_PARAMS = arrayOf("sort", "region", "audience", "status", "decade", "magazine_id")
    }

    private fun String?.ifNotBlank(action: (String) -> Unit) = this?.takeIf(String::isNotBlank)?.let(action)

    private fun browseUrl(page: Int, query: String?, filters: FilterList): HttpUrl {
        val url = "$baseUrl/api/pages/mangas/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "24")
        query.ifNotBlank { url.addQueryParameter("search", it) }
        filters.forEachIndexed { i, filter -> filter.toString().ifNotBlank { url.addQueryParameter(FILTER_PARAMS[i], it) } }
        return url.build()
    }

    private fun parseBrowse(response: Response): MangasPage {
        val list = response.parseAs<JsonObject>().getObject("list")
        val manga = list.getArray("items").map { it.parseAs<MangaItem>().toSManga() }
        val hasNextPage = with(list.getObject("meta")) { getInt("page") < getInt("total_pages") }
        return MangasPage(manga, hasNextPage)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val filters = FilterList(SortFilter(Filter.Sort.Selection(1, false)))
        val response = client.get(browseUrl(page, null, filters))
        return parseBrowse(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val filters = FilterList(SortFilter(Filter.Sort.Selection(0, false)))
        val response = client.get(browseUrl(page, null, filters))
        return parseBrowse(response)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        RegionFilter(),
        AudienceFilter(),
        StatusFilter(),
        DecadeFilter(),
        MagazineFilter(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.get(browseUrl(page, query, filters))
        return parseBrowse(response)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/mangas/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/mangas/${chapter.memo.getString("cid")}/read/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get("$baseUrl/api/pages/mangas/${manga.url}").parseAs<MangaData>()
        val sManga = data.manga.toSManga(data.people(), data.tags())
        val sChapters = data.chapters.map { it.toSChapter(manga.url, manga.memo.getLong("updateAt")) }
        return SMangaUpdate(sManga, sChapters.reversed())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val cid = chapter.memo.getString("cid")
        val response = client.get("$baseUrl/api/pages/mangas/reader/$cid/${chapter.url}", ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            if (response.code == 401) throw Exception("请先在 WebView 中登录") else throw HttpException(response.code)
        }
        return response.parseAs<JsonObject>().getObject("manifest").getArray("pages").mapIndexed { index, page ->
            with(page.obj) {
                val pid = getString("id")
                val mimeType = getString("mime_type")
                Page(index, imageUrl = "$baseUrl/api/v3/reader/mangas/$cid/chapters/${chapter.url}/pages/$pid/content#$mimeType")
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        val token = ImageInterceptor.newToken()
        return POST("${page.imageUrl!!}|$token", headers, buildJsonObject { put("p", token) }.toJsonRequestBody<JsonObject>())
    }
}
