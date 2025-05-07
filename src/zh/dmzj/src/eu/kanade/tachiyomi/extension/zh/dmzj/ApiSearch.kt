package eu.kanade.tachiyomi.extension.zh.dmzj

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

object ApiSearch {

    fun searchUrlV1(page: Int, query: String) =
        "https://manhua.idmzj.com/api/v1/comic2/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "30")
            .addQueryParameter("keyword", query)
            .toString()

    fun parsePageV1(response: Response): MangasPage {
        if (!response.isSuccessful) {
            response.close()
            return MangasPage(emptyList(), false)
        }
        val result = response.parseAs<ResponseDto<SearchResultDto?>>()
        if (result.errmsg.isNotBlank()) {
            throw Exception(result.errmsg)
        } else {
            val url = response.request.url
            val page = url.queryParameter("page")?.toInt()
            val size = url.queryParameter("size")?.toInt()
            return if (result.data != null) {
                MangasPage(result.data.comicList.map { it.toSManga() }, page!! * size!! < result.data.totalNum)
            } else {
                MangasPage(emptyList(), false)
            }
        }
    }

    @Serializable
    class MangaDtoV1(
        private val id: Int,
        private val name: String,
        private val authors: String,
        private val cover: String,
    ) {
        fun toSManga() = SManga.create().apply {
            url = getMangaUrl(id.toString())
            title = name
            author = authors
            thumbnail_url = cover
        }
    }

    @Serializable
    class SearchResultDto(
        val comicList: List<MangaDtoV1>,
        val totalNum: Int,
    )

    @Serializable
    class ResponseDto<T>(
        val errmsg: String = "",
        val data: T,
    )
}
