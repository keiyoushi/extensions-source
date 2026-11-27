package eu.kanade.tachiyomi.extension.en.cocomic

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Cocomic : Madara() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::wwwrootImageFallback)
        .rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host && !it.encodedPath.startsWith("/wp-content/uploads/") && !it.encodedPath.startsWith("$WWWROOT/") }

    // Reader images under the server's /www/wwwroot path are on either the site or the cdn2 host
    private fun wwwrootImageFallback(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 404 || !request.url.encodedPath.startsWith("$WWWROOT/")) return response
        response.close()
        val cdnUrl = request.url.newBuilder()
            .host("cdn2.${baseUrl.toHttpUrl().host}")
            .encodedPath(request.url.encodedPath.removePrefix(WWWROOT))
            .build()
        return chain.proceed(request.newBuilder().url(cdnUrl).build())
    }

    override val mangaDetailsSelectorTitle = "h1.asura-series-title"
    override val mangaDetailsSelectorAuthor = ".asura-chip-author .author-name-text"
    override val mangaDetailsSelectorArtist = ".asura-chip:has(.chip-label:containsOwn(Artist)) .chip-val"
    override val mangaDetailsSelectorStatus = ".asura-badge-status"
    override val mangaDetailsSelectorDescription = ".asura-synopsis-content"
    override val mangaDetailsSelectorThumbnail = "img.asura-poster-img"
    override val mangaDetailsSelectorGenre = "a.asura-genre-tag"
    override val altNameSelector = "p.asura-series-alt-title"
    override val seriesTypeSelector = ".asura-hero-badge-type"
    override val updatingRegex = Regex("^(?:Updating|TBA)$", RegexOption.IGNORE_CASE)

    // The series and reader pages carry the post id only in the base64 `var manga` script
    override fun Document.mangaId(): String? = selectFirst("script#wp-manga-js-extra[src^=data:text/javascript;base64,]")
        ?.attr("src")
        ?.substringAfter(',')
        ?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
        ?.let { mangaIdRegex.find(it)?.groupValues?.get(1) }

    override val chapterMode = ChapterMode.MangaAjax

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium)"

    override val pageListParseSelector = "div.asura-stream-images img"

    companion object {
        private const val WWWROOT = "/www/wwwroot"
        private val mangaIdRegex = Regex(""""manga_id":"(\d+)"""")
    }
}
