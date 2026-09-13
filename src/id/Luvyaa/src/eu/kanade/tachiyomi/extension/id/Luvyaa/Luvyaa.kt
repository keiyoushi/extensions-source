@file:Suppress("ktlint:standard:package-name")

package eu.kanade.tachiyomi.extension.id.Luvyaa

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Luvyaa :
    MangaThemesia(),
    ConfigurableSource {
    override val datePattern = "dd/MM/yyyy"

    private val preferences by getPreferencesLazy()

    override fun searchMangaUrl(page: Int, query: String) = if (query.isNotEmpty()) {
        baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query)
            addQueryParameter("page", page.toString())
        }
    } else {
        super.searchMangaUrl(page, query)
    }

    override fun chapterListParse(document: Document): List<SChapter> {
        val lockedUrls = LOCKED_URLS_REGEX.find(document.toString())?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim().removeSurrounding("'").removeSurrounding("\"").replace("\\/", "/") }
            .orEmpty()

        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED, false)

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
