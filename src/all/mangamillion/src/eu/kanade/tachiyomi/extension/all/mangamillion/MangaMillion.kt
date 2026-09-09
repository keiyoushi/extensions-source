package eu.kanade.tachiyomi.extension.all.mangamillion

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

@Source
abstract class MangaMillion : KeiSource() {
    private val domain = baseUrl.toHttpUrl().host
    private val apiUrl = "https://api.$domain/api"
    private val serviceLang: String
        get() = if (lang in SERVICE_LANGUAGES) lang else "en"

    private var token: String? = null

    private suspend fun getAccessToken(): String {
        token?.let { return it }

        val url = "$apiUrl/register".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .build()

        val acceptHeaders = Headers.Builder()
            .set("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            .set("Accept", "*/*")
            .build()

        return client.post(url, acceptHeaders, EMPTY_BODY).parseAsProto<TokenResponse>().token.accessToken.also { token = it }
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override fun Headers.Builder.configureHeaders() = apply {
        val accessToken = if (token.isNullOrEmpty()) runBlocking { getAccessToken() } else token!!
        set("Access-Token", accessToken)
        set("Accept", "*/*")
        set("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/manga_list".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .build()

        val result = client.get(url).parseAsProto<SeriesResponse>()
        val mangas = result.allSeries.seriesList
            .filter { lang in it.languages }
            .sortedByDescending { it.series.views }
            .map { it.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/manga_list".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .build()

        val result = client.get(url).parseAsProto<SeriesResponse>()
        val mangas = result.allSeries.seriesList
            .filter { lang in it.languages }
            .sortedByDescending { it.series.uploadTime }
            .map { it.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .addQueryParameter("translated_language", lang)
            .addQueryParameter("text", query)
            .build()

        val result = client.get(url).parseAsProto<SearchResponse>()
        val mangas = result.allSeries.seriesList
            .filter { lang in it.languages }
            .map { it.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangas = async {
            if (!fetchDetails) return@async manga
            val url = "$apiUrl/title_detail".toHttpUrl().newBuilder()
                .addQueryParameter("service_language", serviceLang)
                .addQueryParameter("avif_enable", "true")
                .addQueryParameter("original_title_id", manga.url)
                .build()
            client.get(url).parseAsProto<DetailsResponse>().detailsEntry.details.toSManga()
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val url = "$apiUrl/chapter_list".toHttpUrl().newBuilder()
                .addQueryParameter("service_language", serviceLang)
                .addQueryParameter("avif_enable", "true")
                .addQueryParameter("original_title_id", manga.url)
                .addQueryParameter("translated_language", lang)
                .build()

            client.get(url).parseAsProto<ChapterResponse>().chapterEntry.chapterGroups
                .flatMap { it.chapterList }
                .filter { it.isAvailable }
                .map { it.toSChapter(manga.url) }
                .fixExtraChapterNumbers()
                .reversed()
        }

        SMangaUpdate(
            mangas.await(),
            chapterList.await(),
        )
    }

    private fun List<SChapter>.fixExtraChapterNumbers(): List<SChapter> = apply {
        for (i in indices) {
            val chapter = this[i]
            if (chapter.chapter_number == -1F) {
                chapter.chapter_number = when {
                    i > 0 -> this[i - 1].chapter_number + 0.01F
                    size > 1 -> this[1].chapter_number - 0.01F
                    else -> 0F
                }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("service_language", serviceLang)
            .addQueryParameter("avif_enable", "true")
            .addQueryParameter("translated_chapter_id", chapter.url)
            .addQueryParameter("quality", "middle")
            .build()

        val viewer = client.get(url).parseAsProto<ViewerResponse>().viewer
        return viewer.pageList.mapIndexed { index, page ->
            Page(index, imageUrl = page.imageUrl + "#${viewer.key}:${viewer.iv}")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$serviceLang/title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/$serviceLang/title/${chapter.memo["titleId"]!!.string}/chapter/${chapter.url}"

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
        private val SERVICE_LANGUAGES = setOf(
            "de", "en", "es", "fr", "hi", "id", "it", "ja", "ko-KR", "pt-BR", "ru", "th", "vi", "zh-CN",
        )
    }
}
