package eu.kanade.tachiyomi.extension.en.resetscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraIcmadaraPageFallback
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ResetScans :
    Madara(
        "Reset Scans",
        "https://reset-scans.org",
        "en",
        dateFormat = SimpleDateFormat("dd-MMM", Locale.US),
    ),
    ConfigurableSource {
    // Moved from FuzzyDoodle to Madara
    override val versionId = 3

    override val useNewChapterEndpoint = true

    private val preferences by getPreferencesLazy()

    // Reset Scans runs `wp-manga-chapter-coin` on top of mycred for paid
    // chapters. The icmadara plugin's REST endpoints leak the canonical
    // chapter list and image URLs even for locked chapters — verified
    // live (~36 page chapter survives the lock). Default ON because the
    // lock is purely a paywall on public HTML; the API itself doesn't
    // honor any access check.
    private val restFallback = MadaraIcmadaraPageFallback(defaultEnabled = true)

    // Hide locked chapters from the HTML chapter list (anchors that point
    // at "#"). When the bypass is on, chapterListParse swaps in the
    // icmadara-derived list anyway, so this only affects the disabled-
    // bypass path.
    override fun chapterListSelector() = "li.wp-manga-chapter:not(:has(> a[href*=\"#\"]))"

    override fun chapterListParse(response: Response): List<SChapter> {
        if (restFallback.isEnabled(preferences)) {
            val mangaSlug = mangaSlugFromUrl(response.request.url.toString())
            if (mangaSlug != null) {
                val chapters = restFallback.fetchChaptersFromIcmadara(
                    baseUrl,
                    mangaSlug,
                    client,
                    headers,
                )
                if (chapters.isNotEmpty()) return chapters
            }
        }
        return super.chapterListParse(response)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val capituloId = restFallback.decodeCapituloId(chapter.url)
        if (capituloId != null && restFallback.isEnabled(preferences)) {
            return restFallback.restRequest(baseUrl, capituloId, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val ctype = response.header("Content-Type").orEmpty()
        if ("json" in ctype) {
            return restFallback.parseRestResponse(response, response.request.url.toString())
        }
        return super.pageListParse(response.asJsoup())
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        restFallback.addPreferenceToScreen(screen, intl)
    }

    private fun mangaSlugFromUrl(url: String): String? = MANGA_SLUG_REGEX.find(url)?.groupValues?.get(1)

    companion object {
        private val MANGA_SLUG_REGEX = Regex("""/manga/([^/]+)/?""")
    }
}
