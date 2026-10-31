package eu.kanade.tachiyomi.extension.pt.huntersscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Source
abstract class HuntersScans : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    override fun OkHttpClient.Builder.configureClient() = apply {
        readTimeout(1.minutes)
        addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.request.url.pathSegments.any { segment -> listOf("logar", "registrar").any { it.equals(segment, true) } }) {
                response.close()
                throw IOException("Faça o login na WebView")
            }
            response
        }
        rateLimit(2)
    }

    override val mangaSubString = "read"

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"

    override val chapterMode = ChapterMode.MangaAjaxPaginated

    override fun parsePages(document: Document): List<Page> = document.select("canvas.protected-chapter-canvas[data-page-url]").mapIndexed { index, canvas ->
        val imageUrl = canvas.absUrl("data-page-url").toHttpUrl().newBuilder().scheme("https").build()
        Page(index, document.location(), imageUrl.toString())
    }
}
