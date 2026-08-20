package eu.kanade.tachiyomi.extension.zh.baozimhorg

import eu.kanade.tachiyomi.multisrc.goda.GoDa
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException

@Source
abstract class GoDaManhua : GoDa() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(
        Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code != 404) return@Interceptor response
            response.close()
            throw IOException("请将此漫画重新迁移到本图源")
        },
    )

    override suspend fun fetchChapterList(mangaId: String): List<SChapter> {
        val response = client.get("https://api-get-v3.mgsearcher.com/api/manga/get?mid=$mangaId&mode=all", headers)
        return response.parseAs<ResponseDto<ChapterListDto>>().data.toChapterList()
    }

    override fun pageListUrl(mangaId: String, chapterId: String) = "https://api-get-v3.mgsearcher.com/api/v2/chapter/getinfo?m=$mangaId&c=$chapterId"

    override fun parsePageList(response: Response): List<Page> {
        val info = response.parseAs<ResponseDto<PageListDto>>().data.info
        val decoded = ChapterImageDecoder.decode(info.images.images)
        return decoded.parseAs<List<ImageDto>>().map { it.toPage() }
    }
}
