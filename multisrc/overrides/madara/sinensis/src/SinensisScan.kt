package eu.kanade.tachiyomi.extension.pt.sinensis

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SinensisScan : Madara(
    "Sinensis Scan",
    "https://sinensisscan.net",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    // Name changed from Sinensis to Sinensis Scan
    override val id: Long = 3891513807564817914

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            setUrlWithoutDomain(url.removeBadPath("manga"))
        }
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            setUrlWithoutDomain(url.removeBadPath("manga"))
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            setUrlWithoutDomain(url.removeBadPath("manga"))
        }
    }

    private fun String.removeBadPath(expectedFirstPath: String): String {
        val fullUrl = if (contains(baseUrl)) this else (baseUrl + this)
        val url = fullUrl.toHttpUrl()

        if (url.pathSegments.firstOrNull() != expectedFirstPath) {
            return url.newBuilder().removePathSegment(0).toString()
        }

        return url.toString()
    }
}
