package eu.kanade.tachiyomi.extension.en.mangadistrict

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDistrict : Madara(
    "Manga District",
    "https://mangadistrict.com",
    "en",
) {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun searchMangaNextPageSelector() = "div[role=navigation] a.last"

    private val titleComment = Regex("\\(.*\\)")

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            if (preferences.getBoolean(REMOVE_TITLE_COMMENT_PREF, true)) {
                title = this.title.replace(titleComment, "").trim()
            }
        }
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            if (preferences.getBoolean(REMOVE_TITLE_COMMENT_PREF, true)) {
                title = this.title.replace(titleComment, "").trim()
            }
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            if (preferences.getBoolean(REMOVE_TITLE_COMMENT_PREF, true)) {
                title = this.title.replace(titleComment, "").trim()
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_COMMENT_PREF
            title = "Remove comments from title, e.g. \"(Official)\", \"(Doujinshi)\""
            summary = "To help app find duplicate entries in library"
            setDefaultValue(true)
        }.let(screen::addPreference)

        super.setupPreferenceScreen(screen)
    }

    companion object {
        private const val REMOVE_TITLE_COMMENT_PREF = "REMOVE_TITLE_COMMENT"
    }
}
