package eu.kanade.tachiyomi.extension.en.zinmanganet

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.minutes

@Source
abstract class ZinmangaNet : MadaraNoAjax() {
    override val supportsPostId = false
    override val chapterMode = ChapterMode.MangaAjax

    override val filterNonMangaItems = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(permits = 60, period = 1.minutes) { it.encodedPath.startsWith("/api/") }

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val slug = baseUrl.toHttpUrl().resolve(mangaPath)!!.pathSegments[1]
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/comics")
            .addPathSegment(slug)
            .addPathSegment("chapters")
            .addQueryParameter("per_page", "100")
            .addQueryParameter("order", "desc")
            .build()

        suspend fun fetchPage(page: Int) = client.get(url.newBuilder().addQueryParameter("page", page.toString()).build(), Fetch.XHR).parseAs<ChapterListResponse>().data

        val first = fetchPage(1)
        val rest = coroutineScope {
            (2..first.lastPage).map { async { fetchPage(it).chapters } }.awaitAll()
        }
        return (first.chapters + rest.flatten()).map { it.toSChapter(mangaPath) }.distinctBy(SChapter::url)
    }

    // The image CDN answers 403 when the Referer is a chapter page
    override fun getImageRequest(page: Page) = super.getImageRequest(page).newBuilder()
        .header("Referer", "$baseUrl/")
        .build()
}
