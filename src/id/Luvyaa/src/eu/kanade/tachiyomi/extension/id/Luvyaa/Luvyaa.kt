@file:Suppress("ktlint:standard:package-name")

package eu.kanade.tachiyomi.extension.id.Luvyaa

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Luvyaa :
    MangaThemesia(
        "Luvyaa",
        "https://v4.luvyaa.co",
        "id",
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US),
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val lockedUrls = LOCKED_URLS_REGEX.find(document.toString())?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim().removeSurrounding("'").removeSurrounding("\"").replace("\\/", "/") }
            .orEmpty()

        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED, false)

        countViews(document)

        return document.select(super.chapterListSelector()).mapNotNull { element ->
            val chapter = super.chapterFromElement(element)
            val isLocked = lockedUrls.any { it.endsWith(chapter.url) }

            if (hideLocked && isLocked) return@mapNotNull null

            chapter.apply {
                if (isLocked) {
                    name = "🔒 $name"
                }
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED
            title = "Sembunyikan chapter terkunci"
            summary = "Sembunyikan chapter yang memerlukan membership (VIP)"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_HIDE_LOCKED = "pref_hide_locked_chapters"

        private val LOCKED_URLS_REGEX = """lockedUrls\s*=\s*\[(.*?)]""".toRegex()
    }
}
