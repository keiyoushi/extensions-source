package eu.kanade.tachiyomi.extension.all.thunderscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ThunderScansFactory : SourceFactory {
    override fun createSources() = listOf(
        LavaScans(),
        ThunderScans(),
    )
}

abstract class ThunderScansBase(
    name: String,
    baseUrl: String,
    lang: String,
    mangaUrlDirectory: String = "/manga",
    dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
) : MangaThemesiaAlt(name, baseUrl, lang, mangaUrlDirectory, dateFormat) {
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

    open val searchMangaTitleSelector = ".bigor .tt, h3 a"

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        title = element.selectFirst(searchMangaTitleSelector)?.text()?.takeIf(String::isNotEmpty) ?: element.selectFirst("a")!!.attr("title")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )
}

class LavaScans :
    ThunderScansBase(
        "Lava Scans",
        "https://lavascans.com",
        "ar",
        dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar")),
    ) {
    override val id = 3209001028102012989

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

class ThunderScans :
    ThunderScansBase(
        "Thunder Scans",
        "https://en-thunderscans.com",
        "en",
        mangaUrlDirectory = "/comics",
    )
