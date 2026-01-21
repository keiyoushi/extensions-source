package eu.kanade.tachiyomi.extension.en.madarascans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferencesLazy
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MadaraScans :
    MangaThemesia(
        "Madara Scans",
        "https://madarascans.com",
        "en",
        mangaUrlDirectory = "/series",
        dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US),
    ),
    ConfigurableSource {

    override val dateFormatSelector = "yyyy/MM/dd"

    // support for both popular/latest tabs and search
    override fun searchMangaSelector() = ".listupd, div.legend-inner"

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

    // missing following fields from source
    // override val seriesArtistSelector = ""
    // override val seriesAuthorSelector = ""
    // override val seriesTypeSelector = ""
    // override val altNamePrefix = ""

    override val seriesDescriptionSelector = "div.lh-story > #manga-story"
    override val seriesAltNameSelector = ".fa-info-circle"
    override val seriesGenreSelector = ".lh-genres > .lh-genre-tag"
    override val seriesStatusSelector = "span.status-badge-lux"
    override val seriesThumbnailSelector = ".lh-poster > img"

    // chapters
    override fun chapterListSelector(): String = "#chapters-list-container > div"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".ch-num").text().ifBlank { urlElements.first()!!.text() }
        val dateElement = element.select(".ch-date")?.text()
        date_upload = dateElement.parseChapterDate()
    }

    //todo: fix searching, instead of using /series/title= use /?s=

    override val pageSelector = "div#readerArea img"

    private val preferences by getPreferencesLazy()

    //todo: fix lockedChapterSelector
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(lockedChapterSelector = ".ch-price-side")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }
}
