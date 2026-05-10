package eu.kanade.tachiyomi.extension.en.drakescans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaRestPageFallback
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class DrakeScans :
    MangaThemesia(
        "Drake Scans",
        "https://drakecomic.org",
        "en",
    ),
    ConfigurableSource {
    // madara -> mangathemesia
    override val versionId = 2

    private val preferences by getPreferencesLazy()

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1, TimeUnit.SECONDS)
        .build()

    override fun imageRequest(page: Page): Request {
        val newUrl = page.imageUrl!!.replace(JETPACK_CDN_REGEX, "https://")
        return super.imageRequest(page).newBuilder()
            .url(newUrl)
            .build()
    }

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(lockedChapterSelector = "ul li.locked")

    // Drake's `lock-chapters` plugin redirects locked chapter URLs to the
    // login page, but `/wp-json/wp/v2/posts/<id>` still returns the full
    // content (verified live). The post id is on `<span class="chapternum"
    // data-post-id="…">` in the list. Default OFF because Drake monetizes
    // through paid early-access — leave the bypass opt-in to respect that.
    private val restFallback = MangaThemesiaRestPageFallback(defaultEnabled = false)

    // When the bypass is on, surface locked chapters in the list regardless
    // of the "hide paid chapters" toggle — otherwise users would have to
    // flip two settings to read them.
    override fun chapterListSelector(): String = if (restFallback.isEnabled(preferences)) {
        super.chapterListSelector()
    } else {
        paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
            super.chapterListSelector(),
            preferences,
        )
    }

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).also { chapter ->
        // Both free and locked entries carry the post id on the
        // `<span class="chapternum">` element.
        val postId = element.selectFirst("[data-post-id]")?.attr("data-post-id")
            ?.takeIf { it.isNotEmpty() }
        if (postId != null) {
            chapter.url = restFallback.encodePostId(chapter.url, postId)
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

    companion object {
        val JETPACK_CDN_REGEX = """^https:\/\/i[0-9]\.wp\.com\/""".toRegex()
    }
}
