package eu.kanade.tachiyomi.extension.es.bloomscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class Bloomscans : MangaThemesia() {
    override val mangaUrlDirectory = "/series"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::refreshExpiredImage)

    // Text queries use the site's search box
    override fun searchMangaUrl(page: Int, query: String, filters: FilterList) = if (query.isEmpty()) {
        super.searchMangaUrl(page, query, filters)
    } else {
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("page")
            .addPathSegment(page.toString())
            .addPathSegment("")
            .addQueryParameter("s", query)
    }

    override val seriesTitleSelector = ".lrs-title"
    override val seriesThumbnailSelector = "img.lrs-cover"
    override val seriesDescriptionSelector = ".lrs-syn-wrap"
    override val seriesStatusSelector = ".lrs-infotable tr:contains(Status) td:last-child"
    override val seriesGenreSelector = ".lrs-genre"

    override fun String?.parseStatus(): Int = when (this) {
        "En emisión" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        "Hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#lrs-native-chapterlist li"

    // A POST skips the LiteSpeed page cache, whose copies hold expired image tokens
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.post(getChapterUrl(chapter), Fetch.DOCUMENT, FormBody.Builder().build()).asJsoup()
        sendView(document.postId())
        return pageListParse(document)
    }

    // /reader-image/<chapter id>/<token>/<file>
    private fun refreshExpiredImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val segments = request.url.pathSegments
        if (segments.size != 4 || segments[0] != "reader-image") return chain.proceed(request)
        val (_, chapterId, _, file) = segments

        recentImages(chapterId)?.firstOrNull { it.pathSegments.last() == file }
            ?.let { return chain.proceed(request.newBuilder().url(it).build()) }
        val response = chain.proceed(request)
        if (response.code != 404) return response
        response.close()

        val url = refreshedImages(chapterId).firstOrNull { it.pathSegments.last() == file }
            ?: throw IOException("Imagen no encontrada en el capítulo $chapterId")
        return chain.proceed(request.newBuilder().url(url).build())
    }

    // Last refreshed chapter, so its other expired images need no further request
    @Volatile
    private var refreshed: RefreshedImages? = null

    private fun recentImages(chapterId: String) = refreshed
        ?.takeIf { it.chapterId == chapterId && System.currentTimeMillis() - it.fetchedAt < REFRESHED_TTL }
        ?.urls

    @Synchronized
    private fun refreshedImages(chapterId: String): List<HttpUrl> {
        recentImages(chapterId)?.let { return it }
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("p", chapterId).build()
        val request = POST(url.toString(), headers, FormBody.Builder().build()).newBuilder().fetch(Fetch.DOCUMENT).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Error HTTP ${response.code}")
        }
        val urls = pageListParse(response.asJsoup()).mapNotNull { it.imageUrl?.toHttpUrlOrNull() }
        refreshed = RefreshedImages(chapterId, System.currentTimeMillis(), urls)
        return urls
    }

    private class RefreshedImages(val chapterId: String, val fetchedAt: Long, val urls: List<HttpUrl>)

    companion object {
        private const val REFRESHED_TTL = 60 * 1000L
    }
}
