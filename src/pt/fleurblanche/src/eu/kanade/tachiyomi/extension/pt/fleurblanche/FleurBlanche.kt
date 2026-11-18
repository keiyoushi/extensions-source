package eu.kanade.tachiyomi.extension.pt.fleurblanche

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class FleurBlanche : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authWarningIntercept)
        .rateLimit(1, 2.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }

    override val archiveUrlSelector = ".post-title a:not(:has(.manga-title-badges))"
    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"
    override val mangaDetailsSelectorDescription = "div.manga-summary"

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select("$filterGenresSelector a[href*='/$genreDirectory/']")
        .map {
            val path = it.absUrl("href").toHttpUrl().encodedPath
            GenreRoute(it.text(), path.trimEnd('/').substringAfterLast('/'), path)
        }
        .toJsonElement()

    private fun authWarningIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.toString().contains("wp-login.php")) {
            response.close()
            throw IOException("É necessário realizar o login via WebView para acessar a fonte.")
        }

        return response
    }
}
