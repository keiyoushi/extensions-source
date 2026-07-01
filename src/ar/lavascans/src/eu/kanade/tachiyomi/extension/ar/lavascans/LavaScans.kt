package eu.kanade.tachiyomi.extension.ar.lavascans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class LavaScans : MangaThemesiaAlt() {
    override val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

    open val searchMangaTitleSelector = ".bigor .tt, h3 a"

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        title = element.selectFirst(searchMangaTitleSelector)?.text()?.takeIf(String::isNotEmpty) ?: element.selectFirst("a")!!.attr("title")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        name = element.selectFirst(".ch-num")!!.text().ifEmpty { name }
        date_upload = element.selectFirst(".ch-date")?.text()?.parseChapterDate() ?: date_upload
    }

    override fun searchMangaSelector() = ".listupd .manga-card-v"

    override val seriesDetailsSelector = "div.lh-container"
    override val seriesTitleSelector = ".lh-title"
    override val seriesDescriptionSelector = "#manga-story"
    override val seriesGenreSelector = ".lh-genres a"
    override val seriesStatusSelector = ".status-badge-lux"
    override val seriesThumbnailSelector = ".lh-poster img"

    override fun chapterListSelector(): String {
        val base = "#chapters-list-container .ch-item"
        return if (preferences.getBoolean("pref_hide_paid_chapters", true)) {
            "$base:not(.locked)"
        } else {
            base
        }
    }
}
