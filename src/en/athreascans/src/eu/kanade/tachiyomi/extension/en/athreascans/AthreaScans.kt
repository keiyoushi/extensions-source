package eu.kanade.tachiyomi.extension.en.athreascans

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.getPreferences
import okhttp3.OkHttpClient
import okhttp3.Response

class AthreaScans :
    MangaThemesia("Athrea Scans", "https://athreascans.com", "en"),
    ConfigurableSource {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    private val preferences: SharedPreferences = getPreferences()

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).filterNot { chapter ->
        // Additional filter: skip chapters without valid URLs (locked chapters have no href)
        chapter.url.isBlank() || chapter.url == "#"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }
}
