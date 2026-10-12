package eu.kanade.tachiyomi.extension.zh.vomic

import android.util.Base64
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Vomic : KeiSource() {

    override val supportsLatest = false

    private val apiUrl: String
        get() {
            val host = baseUrl.toHttpUrl().host
            return if (host.startsWith("www.") || host.startsWith("api.")) {
                "https://api.${host.substring(4)}"
            } else {
                baseUrl
            }
        }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        try {
            chain.proceed(chain.request()).use { response ->
                if (response.isSuccessful) {
                    return@addInterceptor response
                }
            }
        } catch (_: Exception) { }

        throw IOException("请在插件设置中修改网址")
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangaList = client.get(
            "$apiUrl/api/v1/rank/rank-data?rank_id=1&page=$page",
        ).parseAs<RankingDto>()
        val entries = mangaList.result.mapNotNull { it.toSMangaOrNull() }
        val hasNextPage = page < 5
        return MangasPage(entries, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchQuery = parseSearchQuery(query.trim(), filters)
        if (searchQuery.title.isEmpty() && searchQuery.category.isEmpty()) throw Exception("请输入搜索词或分类")

        val url = "$apiUrl/api/v1/search/search-comic-data".toHttpUrl().newBuilder()
            .addQueryParameter("title", searchQuery.title)
            .addQueryParameter("category", searchQuery.category)
            .addEncodedQueryParameter("page", page.toString())
            .build()

        val mangaList = client.get(url).parseAs<MangaListDto>()
        val entries = mangaList.entries.mapNotNull { it.toSMangaOrNull() }
        return MangasPage(entries, mangaList.hasNextPage)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/#/detail?id=${manga.id}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                client.get(
                    "$apiUrl/api/v1/detail/get-comic-detail-data?mid=${manga.id}",
                ).parseAs<MangaDto>().toSMangaDetails()
            } else {
                manga
            }
        }

        val chapterList = async {
            if (fetchChapters) {
                val chapters = client.get(
                    "$apiUrl/api/v1/detail/get-comic-detail-chapter-data?mid=${manga.id}",
                ).parseAs<List<ChapterDto>>()
                chapters.map { it.toSChapter(manga.id) }
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, chapterId) = chapter.id
        return "$baseUrl/#/page/$mangaId/$chapterId"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (mangaId, chapterId) = chapter.id
        val key = run {
            val alphanumeric = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            val chars = CharArray(24) { alphanumeric.random() }
            String(chars)
        }
        val time = System.currentTimeMillis().toString()
        val encrypted = run {
            val keySpec = SecretKeySpec(key.toByteArray(), "DESede")
            val iv = "k8tUyS\$m"
            val ivSpec = IvParameterSpec(iv.toByteArray())
            val payload = key + iv + "cid=" + chapterId + "&mid=" + mangaId + time
            val bytes = Cipher.getInstance("DESede/CBC/PKCS5Padding").run {
                init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                doFinal(payload.toByteArray())
            }
            Base64.encodeToString(bytes, Base64.DEFAULT)
        }
        val url = "$apiUrl/api/v2/page/get-comic-page-img-data".toHttpUrl().newBuilder()
            .addEncodedQueryParameter("k", key)
            .addEncodedQueryParameter("t", time)
            .addQueryParameter("e", encrypted)
            .build()

        val response = client.get(url)
        val pageList = response.parseAs<List<String>>()
        if (pageList.size == 1 && pageList[0] == "https://cdn.vomicer.com/qiniu/vomic/otherImg/info2.webp") {
            throw Exception("无法阅读此章节")
        }
        return pageList.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun getFilterList(data: JsonElement?) = getFilterListInternal()
}
