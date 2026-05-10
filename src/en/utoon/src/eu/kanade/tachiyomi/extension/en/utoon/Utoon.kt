package eu.kanade.tachiyomi.extension.en.utoon

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
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Utoon :
    Madara(
        "Utoon",
        "https://utoon.net",
        "en",
        SimpleDateFormat("dd MMM yyyy", Locale.US),
    ),
    ConfigurableSource {
    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    private val preferences by getPreferencesLazy()

    // Utoon also runs the icmadara plugin (verified live). Locked chapters
    // are marked `.premium-block` in the HTML; the icmadara API returns
    // them anyway. Default OFF here because Utoon actually has a working
    // coin-purchase flow — leave the bypass behind a deliberate toggle.
    private val restFallback = MadaraIcmadaraPageFallback(defaultEnabled = false)

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        // Unfortunately Utoon doesn't include the year in the upload date.
        // As a workaround, assume it's from the current year, or last year
        // if the date is in the future.
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val upload = element.selectFirst("span a")?.attr("title")?.let { parseRelativeDate(it) } ?: parseChapterDate("${element.selectFirst(chapterDateSelector())?.text()} $currentYear")
        val now = System.currentTimeMillis()
        date_upload = if (now < upload) {
            parseChapterDate("${element.selectFirst(chapterDateSelector())?.text()} ${currentYear - 1}")
        } else {
            upload
        }
    }

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
