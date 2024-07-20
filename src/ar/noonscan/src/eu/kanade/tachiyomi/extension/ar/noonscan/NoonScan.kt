package eu.kanade.tachiyomi.extension.ar.noonscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class NoonScan : MangaThemesia(
    "نون سكان",
    "https://noonscan.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
) {
    // override val projectPageString = "/project" // 7
    // override val seriesDetailsSelector = "div.bigcontent, div.animefull, div.main-info, div.postbody" // 6
    // override val seriesTitleSelector = "h1.entry-title, .ts-breadcrumb li:last-child span" // 11
    // override val seriesArtistSelector = ... // 12
    // override val seriesAuthorSelector = ... // 24
    // override val seriesDescriptionSelector = ".desc, .entry-content[itemprop=description]" // 7
    // override val seriesAltNameSelector = ... // 3
    // override val seriesGenreSelector = ... // 8
    // override val seriesTypeSelector = ... // 17
    // override val seriesStatusSelector = ... // 21
    // override val seriesThumbnailSelector = ".infomanga > div[itemprop=image] img, .thumb img" // 4
    // override val altNamePrefix = "${intl["alt_names_heading"]} " // 9
    // override fun String?.parseStatus(): Int = ... // 14
    // override fun String?.parseChapterDate(): Long = ... // 1
    // override val pageSelector = "div#readerarea img" // 9
    // override val sendViewCount: Boolean = true // 5
    // override class SelectFilter(...) = ...
    // override val statusOptions = arrayOf(...) // 0
    // override val typeFilterOptions = arrayOf(...) // 0
    // override val orderByFilterOptions = arrayOf(...) // 2
    // override val projectFilterOptions = arrayOf(...) // 0
    // override fun getGenreList(): List<Genre> = ... // 16
    // override val hasProjectPage = false // 41
    // override fun Element.imgAttr(): String = when Ellipsis // 1
}
