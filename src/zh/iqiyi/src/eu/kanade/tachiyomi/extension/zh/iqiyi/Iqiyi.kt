package eu.kanade.tachiyomi.extension.zh.iqiyi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.Response
import java.security.MessageDigest

@Source
abstract class Iqiyi : KeiSource() {

    override val supportsLatest = false

    private val qiyiId = List(32) { HEX.random() }.joinToString("")

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = api("/views/1.0/classify/popularity_list", "type" to "1", "pageSize" to "100", "pageNo" to "1")
            .parseAs<ApiResponse<PopularityListDto>>().data.popularityList
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // The search API only ever returns a single page of results
        val mangas = api("/views/1.0/search", "key" to query, "page_num" to page.toString())
            .parseAs<ApiResponse<SearchDto>>().data.docinfos
            .mapNotNull { it.albumDocInfo?.comics?.toSManga() }
        return MangasPage(mangas, false)
    }

    // Details

    override fun getMangaUrl(manga: SManga) = "$baseUrl/detail/${manga.comicId()}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = api("/views/1.0/comicDetail", "comicId" to manga.comicId())
            .parseAs<ApiResponse<ComicDetailDto>>().data
        return SMangaUpdate(dto.toSManga(), dto.toChapterList())
    }

    private fun SManga.comicId() = url.substringAfter("detail_").substringBefore(".html")

    // Pages

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/detail/${chapter.url.substringAfter("/reader/").substringBefore("_")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val episodeId = chapter.url.substringAfter("_").substringBefore(".html")
        return api("/read/pcw/1.0/read", "episodeId" to episodeId)
            .parseAs<ApiResponse<ReadDto>>().data.toPageList()
    }

    // API

    private suspend fun api(path: String, vararg params: Pair<String, String>): Response {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(API_HOST)
            .encodedPath(path)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .addQueryParameter("qiyiId", qiyiId)
            .addQueryParameter("timeStamp", System.currentTimeMillis().toString())
            .addQueryParameter("srcPlatform", "15")
            .addQueryParameter("appVer", "3.0.0")
            .addQueryParameter("agentVersion", "h5")
            .addQueryParameter("agentType", "115")
            .build()
        // Same request signature as the H5 site: md5(path + query + key)
        val sign = MessageDigest.getInstance("MD5")
            .digest((url.encodedPath + url.encodedQuery + API_KEY).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return client.get(url, headersBuilder().set("md5", sign).build())
    }

    companion object {
        private const val API_HOST = "comic.iqiyi.com"
        private const val API_KEY = "3sj8xof48xjf4tk9f4tk9ypgk9ypg5up"
        private const val HEX = "0123456789abcdef"
    }
}
