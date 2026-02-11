package eu.kanade.tachiyomi.extension.es.mangacrab

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaCrab :
    Madara(
        "Manga Crab",
        "https://mangacrab.org",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale("es")),
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences = getPreferences()

    override val client = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(1, 2)
        .build()

    override val mangaSubString = "series"
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaSelector() = "div.manga__item"
    override val popularMangaUrlSelector = "div.post-title a"
    override val popularMangaUrlSelectorImg = "div.manga__thumb_item img"
    override fun chapterListSelector() = "div.listing-chapters_wrap > ul > li"
    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorDescription = "div.c-page__content div.modal-contenido"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override val pageListParseSelector = "div.page-break:not([style*='display:none']) img:not([src])"

    private val imageKeyRegex = """'Img-X'\s*:\s*'(.*?)'""".toRegex()

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val imageKey = document.selectFirst("script:containsData('Img-X')")?.data()
            ?.let { script ->
                imageKeyRegex.find(script)?.groups?.get(1)?.value
            }
            ?: throw Exception("Image key not found")

        return document.select(pageListParseSelector).mapIndexed { index, element ->
            val imageUrl = element.selectFirst("img")?.let { imageFromElement(it) }
            Page(index, document.location(), "$imageUrl#$imageKey")
        }
    }

    override fun imageFromElement(element: Element): String? {
        val url = element.attributes()
            .firstNotNullOfOrNull { attr ->
                element.absUrl(attr.key).toHttpUrlOrNull()
                    ?.takeIf { it.encodedQuery.toString().contains("wp-content") }
            }

        return when {
            url != null -> url.toString()
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").getSrcSetImage()
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            element.hasAttr("data-src-base64") -> element.attr("abs:data-src-base64")
            else -> element.attr("abs:src")
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl!!.substringBeforeLast("#")
        val imageKey = page.imageUrl!!.substringAfterLast("#")
        val headers = headers.newBuilder()
            .set("Referer", page.url)
            .set("Img-X", imageKey)
            .build()

        return GET(imageUrl, headers)
    }
}
