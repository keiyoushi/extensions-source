package eu.kanade.tachiyomi.extension.en.madarascans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import org.jsoup.nodes.Element
import kotlin.getValue

@Source
abstract class MadaraScans :
    MangaThemesia(),
    ConfigurableSource {
    override val mangaUrlDirectory = "/series"

    override val datePattern = "yyyy/MM/dd"

    private val preferences by getPreferencesLazy()
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(lockedChapterSelector = ".locked")

    // support for both popular/latest tabs and search
    override fun searchMangaSelector() = "div.listupd>div, div.legend-inner"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").imgAttr()
        // support for both popular/latest tabs and search
        val titleElement = element.select("h3.card-v-title > a, h3.legend-title > a")
        title = titleElement.text()
        setUrlWithoutDomain(titleElement.attr("href"))
    }

    // manga details
    override val seriesDetailsSelector = "div.lh-container"
    override val seriesTitleSelector = "h1.lh-title"
    override val seriesDescriptionSelector = "div.lh-story > #manga-story"
    override val seriesAltNameSelector = ".fa-info-circle"
    override val seriesGenreSelector = ".lh-genres > .lh-genre-tag"
    override val seriesStatusSelector = "span.status-badge-lux"
    override val seriesThumbnailSelector = ".lh-poster > img"

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        ".ch-item",
        preferences,
    )

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        val chapterName = element.select(".ch-num").text().ifBlank { urlElements.firstOrNull()?.text().orEmpty() }
        name = if (!element.hasClass("free")) "🔒 $chapterName" else chapterName
        val dateElement = element.select(".ch-date").text()
        date_upload = dateElement.parseChapterDate()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    override val pageSelector = ".pagination, .legendary-pagination, .magma-pagination"
}
