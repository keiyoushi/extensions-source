package eu.kanade.tachiyomi.extension.zh.baozimhorg

import eu.kanade.tachiyomi.multisrc.goda.GoDa
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.IOException

@Source
abstract class GoDaManhua : GoDa() {

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/").add("Origin", baseUrl)

    override val client = super.client.newBuilder().addInterceptor(NotFoundInterceptor()).build()

    override fun fetchChapterList(mangaId: String): List<SChapter> {
        val response = client.newCall(GET("https://api-get-v3.mgsearcher.com/api/manga/get?mid=$mangaId&mode=all", headers)).execute()
        return response.parseAs<ResponseDto<ChapterListDto>>().data.toChapterList()
    }

    override fun pageListRequest(mangaId: String, chapterId: String): Request {
        if (mangaId.isEmpty() || chapterId.isEmpty()) throw Exception("请刷新漫画")
        return GET("https://api-get-v3.mgsearcher.com/api/v2/chapter/getinfo?m=$mangaId&c=$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val info = response.parseAs<ResponseDto<PageListDto>>().data.info
        val decoded = ChapterImageDecoder.decode(info.images.images)
        return decoded.parseAs<List<ImageDto>>().map { it.toPage() }
    }
}

private class NotFoundInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 404) return response
        response.close()
        throw IOException("请将此漫画重新迁移到本图源")
    }
}
