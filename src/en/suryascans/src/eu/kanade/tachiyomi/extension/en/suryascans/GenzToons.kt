package eu.kanade.tachiyomi.extension.en.suryascans

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GenzToons :
    Keyoapp(
        "Genz Toons",
        "https://genzupdates.com",
        "en",
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun chapterListSelector(): String {
        if (!preferences.showPaidChapters) {
            return "#chapters > a:not(:has(.text-sm span:matches(Upcoming))):not(:has(img[src*=Coin.svg]))"
        }
        return "#chapters > a:not(:has(.text-sm span:matches(Upcoming)))"
    }

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            if (element.select("img[src*=Coin.svg]").isNotEmpty()) {
                name = "🔒 $name"
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("#pages > script").joinToString("\n") { it.data() }
        val realCdnUrl = CDN_URL_REGEX.find(script)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
            ?: "$baseUrl/uploads/"
        return document.select("#pages > img")
            .mapIndexed { index, img ->
                Page(index, document.location(), realCdnUrl + img.attr("uid"))
            }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = "Display paid chapters"
            summaryOn = "Paid chapters will appear."
            summaryOff = "Only free chapters will be displayed."
            setDefaultValue(SHOW_PAID_CHAPTERS_DEFAULT)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.showPaidChapters: Boolean
        get() = getBoolean(SHOW_PAID_CHAPTERS_PREF, SHOW_PAID_CHAPTERS_DEFAULT)

    companion object {
        private const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        private const val SHOW_PAID_CHAPTERS_DEFAULT = false
        private val CDN_URL_REGEX = """realUrl\s*=\s*`([^`]+)\$""".toRegex()
    }
}
