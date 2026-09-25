package eu.kanade.tachiyomi.extension.pt.huntersscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
        addInterceptor(::imageInterceptor)
        rateLimit(2)
    }

    override val mangaSubString = "comics"

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"

    override val chapterMode = ChapterMode.MangaAjaxPaginated

    override fun parsePages(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(_HuntersOpts)")?.data()
            ?: return super.parsePages(document)

        val payload = PAYLOAD_REGEX.find(script)?.groupValues?.get(1)
        val sk = SK_REGEX.find(script)?.groupValues?.get(1)

        if (payload != null && sk != null) {
            try {
                val urls = HuntersScanDescrambler.decryptHuntersPayload(payload, sk)
                return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
            } catch (e: Exception) {
            }
        }

        return super.parsePages(document)
    }

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.toString().contains("scrambler.php")) {
            val scrambleKeyHeader = response.header("X-Scramble-Key")
            if (scrambleKeyHeader != null) {
                val imageStream = HuntersScanDescrambler.unscrambleImage(response.body.byteStream(), scrambleKeyHeader)
                val body = imageStream.readBytes().toResponseBody("image/jpeg".toMediaType())
                return response.newBuilder()
                    .body(body)
                    .build()
            }
        }

        return response
    }

    companion object {
        private val PAYLOAD_REGEX = Regex("""payload:\s*"(.*?)"""")
        private val SK_REGEX = Regex("""sk:\s*"(.*?)"""")
    }
}
