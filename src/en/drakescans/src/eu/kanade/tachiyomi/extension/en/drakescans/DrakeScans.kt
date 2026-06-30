package eu.kanade.tachiyomi.extension.en.drakescans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.getValue
import kotlin.time.Duration.Companion.seconds

@Source
abstract class DrakeScans :
    MangaThemesia(),
    ConfigurableSource {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private val preferences by getPreferencesLazy()

    override val client = super.client.newBuilder()
        .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
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
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )
}
