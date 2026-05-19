package eu.kanade.tachiyomi.extension.en.asmotoon

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.asmotoon.waybackmachineinterceptor.WaybackMachineInterceptor
import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.Locale

class Asmotoon :
    Keyoapp(
        "Asmodeus Scans",
        "https://asmotoon.com",
        "en",
    ) {
    // filtering novel entries
    override fun popularMangaSelector() = "div:contains(Trending) + div .group.overflow-hidden.grid:not(:has(.capitalize:contains(Novel)))"
    override fun latestUpdatesSelector() = "div.grid > div.group:not(:has(.capitalize:contains(Novel)))"
    override fun searchMangaSelector() = "#searched_series_page > button:not(:has(.capitalize:contains(Novel)))"

    override val descriptionSelector: String = "#expand_content"
    override val genreSelector: String = ".gap-3 .gap-1 a"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        genre = buildList {
            document.selectFirst(typeSelector)?.text()?.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(
                        Locale.ENGLISH,
                    )
                } else {
                    it.toString()
                }
            }.let(::add)
            document.select(genreSelector).forEach { add(it.text().removeSuffix(",")) }
        }.joinToString()
    }

    val waybackMachineClient: OkHttpClient = super
        .client
        .newBuilder()
        .addInterceptor(WaybackMachineInterceptor("""\w+://asmotoon\.com/.*""".toRegex()))
        .followRedirects(false)
        .build()

    override val client: OkHttpClient get() = if (preferences.getUseWaybackMachinePref()) {
        waybackMachineClient
    } else {
        super.client
    }

    private fun SharedPreferences.getUseWaybackMachinePref(): Boolean = getBoolean(
        PREF_USE_WAYBACK_MACHINE,
        false,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_USE_WAYBACK_MACHINE
            title = "Use WayBack Machine (web.archive.org)"
            summaryOff = "Requests are not redirected to web.archive.org"
            summaryOn = "Requests are redirected to web.archive.org"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_USE_WAYBACK_MACHINE = "pref_use_wayback_machine"
    }
}
