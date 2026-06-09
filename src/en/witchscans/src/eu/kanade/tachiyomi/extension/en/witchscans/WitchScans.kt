package eu.kanade.tachiyomi.extension.en.witchscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaRestPageFallback
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class WitchScans :
    MangaThemesia(
        "WitchScans",
        "https://witchscans.com",
        "en",
    ),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    // The site's `wp-manga-auth-profiles` plugin replaces the `<a href>` of
    // locked chapters in the list with a Bootstrap-modal `<a data-id="…">`,
    // and strips `<img>` tags from the locked chapter HTML. Both the post
    // id (in `data-id`) and the canonical chapter content (via WP REST API)
    // are intact; we wire them together via the bypass helper.
    private val restFallback = MangaThemesiaRestPageFallback(
        // Default ON: WitchScans's "lock" has no payment flow on the
        // chapter (no early-access timer, no purchase callback) — it
        // only gates visibility. Bypassing is the user-friendly default.
        defaultEnabled = true,
    )

    // The locked-chapter anchor on this site uses the same Bootstrap
    // selector as the parent helper's default (`a[data-bs-target='#lockedChapterModal']`),
    // so no override is needed for the selector.
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

    // The default selector requires `:has(a[href])`, which would exclude
    // locked entries. Drop that constraint so the bypass can see them.
    // When the user opts out of bypass, defer to the paid-chapter helper
    // so they can still hide locked chapters from the list.
    override fun chapterListSelector(): String {
        val baseSelector = "div.eplister ul li:has(div.chbox):has(div.eph-num)"
        return if (restFallback.isEnabled(preferences)) {
            baseSelector
        } else {
            paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(baseSelector, preferences)
        }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        // Locked entries have `<a data-bs-target="#lockedChapterModal" data-id="N">`
        // (no href). Free entries have `<a href="…">` (no data-id).
        val lockAnchor = element.selectFirst("a[data-id]")
        val postId = lockAnchor?.attr("data-id")?.takeIf { it.isNotEmpty() }
        val href = element.selectFirst("a[href]")?.attr("href").orEmpty()

        when {
            href.isNotEmpty() -> setUrlWithoutDomain(href)
            postId != null -> setUrlWithoutDomain(restFallback.synthesizeUrlForLockedChapter(postId))
        }
        name = element.selectFirst(".chapternum, .lch a")?.text()
            ?.takeIf { it.isNotBlank() }
            ?: lockAnchor?.attr("data-title").orEmpty()
        date_upload = element.selectFirst(".chapterdate")?.text().parseChapterDate()

        if (postId != null) {
            url = restFallback.encodePostId(url, postId)
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val postId = restFallback.decodePostId(chapter.url)
        if (postId != null && restFallback.isEnabled(preferences)) {
            return restFallback.restRequest(baseUrl, postId, headers)
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
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
        restFallback.addPreferenceToScreen(screen, intl)
    }

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().filterNot { it is AuthorFilter || it is YearFilter }
        return FilterList(filters)
    }
}
