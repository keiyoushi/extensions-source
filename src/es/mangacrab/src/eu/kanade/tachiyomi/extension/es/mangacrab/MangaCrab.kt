package eu.kanade.tachiyomi.extension.es.mangacrab

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferences
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaCrab :
    Madara(
        "Manga Crab",
        "https://mangacrab.topmanhuas.org",
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

    override val pageListParseSelector = "div.page-break:not([style*='display:none'])"

    override fun imageFromElement(element: Element): String? {
        val imageAbsUrl = element.attributes().find { it.key.startsWith("data-img-") }?.value

        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").getSrcSetImage()
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            element.hasAttr("data-src-base64") -> element.attr("abs:data-src-base64")
            imageAbsUrl != null -> imageAbsUrl
            else -> element.attr("abs:src")
        }
    }
}
