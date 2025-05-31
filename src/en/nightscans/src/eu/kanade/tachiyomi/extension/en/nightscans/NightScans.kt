package eu.kanade.tachiyomi.extension.en.nightscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class NightScans : MangaThemesiaAlt("NIGHT SCANS", "https://nightsup.net", "en", "/series") {

    override val listUrl = "/manga/list-mode"
    override val slugRegex = Regex("""^(\d+(st)?-)""")

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4, TimeUnit.SECONDS)
        .build()

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

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

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).distinctBy { it.imageUrl }
    }
}
