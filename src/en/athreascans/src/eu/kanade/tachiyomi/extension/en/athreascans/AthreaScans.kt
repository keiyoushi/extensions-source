package eu.kanade.tachiyomi.extension.en.athreascans

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class AthreaScans :
    MangaThemesia(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    private val preferences: SharedPreferences = getPreferences()

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )

    override fun chapterListParse(document: Document) = super.chapterListParse(document).filterNot { chapter ->
        // Additional filter: skip chapters without valid URLs (locked chapters have no href)
        chapter.url.isBlank() || chapter.url == "#"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }
}
