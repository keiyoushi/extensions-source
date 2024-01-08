package eu.kanade.tachiyomi.extension.en.xcalibrscans

import android.util.Log
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class xCaliBRScans : MangaThemesia("xCaliBR Scans", "https://xcalibrscans.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(AntiScrapInterceptor())
        .rateLimit(2)
        .build()

    override val hasProjectPage = true

    override fun pageListParse(document: Document): List<Page> {
        document.selectFirst("div#readerarea .sword_box") ?: return super.pageListParse(document)

        val imgUrls = mutableListOf<String>()

        // Selects all direct descendant of "div#readerarea"
        document.select("div#readerarea > *")
            .forEach { element ->
                when {
                    element.tagName() == "p" -> {
                        val imgUrl = element.selectFirst("img")!!.imgAttr()
                        imgUrls.add(imgUrl)
                    }
                    element.tagName() == "div" && element.hasClass("kage") -> {
                        parseAntiScrapScramble(element, imgUrls)
                    }
                    else -> {
                        Log.d("xCaliBR Scans", "Unknown element for page parsing $element")
                    }
                }
            }

        return imgUrls.mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl) }
    }

    private fun parseAntiScrapScramble(element: Element, destination: MutableList<String>) {
        element.select("div.sword")
            .forEach { swordDiv ->
                val imgUrls = swordDiv.select("img").map { it.imgAttr() }
                val urls = imgUrls.joinToString(AntiScrapInterceptor.IMAGE_URLS_SEPARATOR)
                val url = baseUrl.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("urls", urls)
                    .fragment(AntiScrapInterceptor.ANTI_SCRAP_FRAGMENT)
                    .build()
                    .toString()

                destination.add(url)
            }
    }
}
