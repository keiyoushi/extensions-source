package eu.kanade.tachiyomi.extension.zh.terrahistoricus

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
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class TerraHistoricus : KeiSource() {
    private val topicKeys = listOf("terra-historicus", "talos-ii-historicus")

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::refreshExpiredImage)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val topicKey = topicKeys[page - 1]
        val comics = fetch<List<THComic>>("$baseUrl/api/comic?topicKey=$topicKey")
        return MangasPage(comics.map { it.toSManga(topicKey) }, topicKey != topicKeys.last())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val topicKey = topicKeys[page - 1]
        val updates = fetch<List<THRecentUpdate>>("$baseUrl/api/recentUpdate?topicKey=$topicKey")
        return MangasPage(updates.map { it.toSManga(topicKey) }, topicKey != topicKeys.last())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangasPage = getPopularManga(page)
        val mangas = mangasPage.mangas.filter { it.title.contains(query) }
        return MangasPage(mangas, mangasPage.hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String {
        val topicKey = manga.memo[TOPIC_KEY]?.stringOrNull ?: return baseUrl + manga.url.removePrefix("/api")
        return "$baseUrl/$topicKey/comic/${manga.url.substringAfterLast('/')}"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val topicKey = chapter.memo[TOPIC_KEY]?.stringOrNull ?: return baseUrl + chapter.url.removePrefix("/api")
        val chapterPath = chapter.url.removePrefix("/api")
        return "$baseUrl/$topicKey$chapterPath"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = fetch<THComic>(baseUrl + manga.url)
        val topicKey = manga.memo[TOPIC_KEY]?.stringOrNull ?: findTopicKey(comic.cid)
        val details = comic.toSManga(topicKey).apply { url = manga.url }
        return SMangaUpdate(details, comic.toSChapterList(topicKey))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val episode = fetch<THEpisode>(baseUrl + chapter.url)
        return (0 until episode.pageInfos!!.size).map {
            Page(it, "$baseUrl${chapter.url}/page?pageNum=${it + 1}")
        }
    }

    override suspend fun getImageUrl(page: Page): String = client.get(page.url).pageImageUrl()
        .toHttpUrl().newBuilder().fragment(page.url).build().toString()

    private fun refreshExpiredImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val pageApiUrl = request.url.fragment
        if (response.code != 403 || pageApiUrl == null) return response

        response.close()
        val freshImageUrl = chain.proceed(Request.Builder().url(freshPageUrl(pageApiUrl)).headers(headers).build())
            .pageImageUrl()
        return chain.proceed(request.newBuilder().url(freshImageUrl).build())
    }

    private fun Response.pageImageUrl(): String = use { it.parseAs<THResult<THPage>>().data.url }

    private fun freshPageUrl(pageApiUrl: String): String = pageApiUrl.toHttpUrl().newBuilder()
        .addQueryParameter("_", System.currentTimeMillis().toString())
        .build()
        .toString()

    private suspend fun findTopicKey(comicCid: String): String {
        for (topicKey in topicKeys) {
            val comics = fetch<List<THComic>>("$baseUrl/api/comic?topicKey=$topicKey")
            if (comics.any { it.cid == comicCid }) return topicKey
        }
        return topicKeys.first()
    }

    private suspend inline fun <reified T> fetch(url: String) = client.get(url).parseAs<THResult<T>>().data

    companion object {
        const val TOPIC_KEY = "topicKey"

        fun buildMemo(topicKey: String): JsonObject = buildJsonObject {
            put(TOPIC_KEY, topicKey)
        }
    }
}
