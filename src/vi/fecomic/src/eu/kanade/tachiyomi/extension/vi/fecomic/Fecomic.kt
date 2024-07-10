package eu.kanade.tachiyomi.extension.vi.fecomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Fecomic : Madara(
    "Fecomic",
    "https://fecomicc.xyz",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false

    override val mangaSubString = "comic"

    override val mangaDetailsSelectorStatus = "div.post-status"
    override val mangaDetailsSelectorDescription = "div.desc div.more"
    override val mangaDetailsSelectorGenre = "div.genres a"

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            // Skip 301 redirect
            url = url.asHttps()
            thumbnail_url = thumbnail_url.asHttpsOrNull()
        }
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            // Skip 301 redirect
            url = url.asHttps()
            thumbnail_url = thumbnail_url.asHttpsOrNull()
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            // Skip 301 redirect
            val httpUrl = url.toHttpUrl()
            // Removes trailing slash, keeps query parameters
            if (httpUrl.pathSegments.lastOrNull()?.isEmpty() == true) {
                url = httpUrl.newBuilder().removePathSegment(httpUrl.pathSegments.size - 1).build().toString()
            }
        }
    }

    private fun String.asHttps(): String {
        return if (this.startsWith("http://")) {
            "https://${this.removePrefix("http://")}"
        } else {
            this
        }
    }

    private fun String?.asHttpsOrNull(): String? {
        return this?.asHttps()
    }
}
