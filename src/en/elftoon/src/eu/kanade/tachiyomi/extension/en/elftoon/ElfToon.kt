package eu.kanade.tachiyomi.extension.en.elftoon

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaRestPageFallback
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class ElfToon :
    MangaThemesia("Elf Toon", "https://elftoon.com", "en"),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    // Locked entries are tagged with the `.gem-price-icon` element. The
    // existing extension was already filtering them out via this selector;
    // we keep the option but expose it as a togglable preference.
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(
        lockedChapterSelector = ":has(.gem-price-icon)",
    )

    // Same `wp-manga-auth-profiles` plugin as WitchScans — strips img tags
    // from locked chapter HTML but leaves the WP REST API untouched. Same
    // default rationale: the lock has no working payment flow on the
    // chapter (the buy button currently just redirects to login).
    private val restFallback = MangaThemesiaRestPageFallback(defaultEnabled = true)

    override fun chapterListSelector(): String {
        val baseSelector = "#chapterlist li"
        return if (restFallback.isEnabled(preferences)) {
            baseSelector
        } else {
            paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(baseSelector, preferences)
        }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        // Locked entries replace the chapter <a href> with a Bootstrap-modal
        // anchor that carries the WP post id on `data-id`. Free entries
        // have a normal href and no data-id.
        val lockAnchor = element.selectFirst("a[data-id]")
        val postId = lockAnchor?.attr("data-id")?.takeIf { it.isNotEmpty() }
        val href = element.selectFirst("a[href]:not([href=\"#\"])")?.attr("href").orEmpty()

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

    override fun imageRequest(page: Page): Request {
        // The site rewrites image URLs through Jetpack's `i[0-3].wp.com`
        // image proxy. Strip that prefix so we hit the origin directly.
        val newUrl = page.imageUrl!!.replace(JETPACK_CDN_REGEX, "https://")
        return super.imageRequest(page).newBuilder()
            .url(newUrl)
            .build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
        restFallback.addPreferenceToScreen(screen, intl)
    }

    companion object {
        private val JETPACK_CDN_REGEX = """^https://i[0-9]\.wp\.com/""".toRegex()
    }
}
