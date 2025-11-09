package eu.kanade.tachiyomi.extension.en.drakescans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DrakeScans : MangaThemesiaAlt(
    "Drake Scans",
    "https://drakecomic.org",
    "en",
) {
    // madara -> mangathemesia
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1, TimeUnit.SECONDS)
        .build()

    override fun imageRequest(page: Page): Request {
        val newUrl = page.imageUrl!!.replace(JETPACK_CDN_REGEX, "https://")
        return super.imageRequest(page).newBuilder()
            .url(newUrl)
            .build()
    }

    companion object {
        val JETPACK_CDN_REGEX = """^https:\/\/i[0-9]\.wp\.com\/""".toRegex()
    }

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(lockedChapterSelector = "ul li.locked")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    override fun chapterListSelector(): String {
        return paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
            super.chapterListSelector(),
            preferences,
        )
    }
}
