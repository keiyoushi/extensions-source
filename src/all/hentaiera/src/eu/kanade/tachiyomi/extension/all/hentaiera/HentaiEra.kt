package eu.kanade.tachiyomi.extension.all.hentaiera

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.SearchFlagFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiEra : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "es" -> LANGUAGE_SPANISH
        "fr" -> LANGUAGE_FRENCH
        "ko" -> LANGUAGE_KOREAN
        "de" -> LANGUAGE_GERMAN
        "ru" -> LANGUAGE_RUSSIAN
        "all" -> LANGUAGE_MULTI
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    override val useIntermediateSearch = true
    override val supportSpeechless = true

    override val mangaTitleSelector = ".gallery_title"

    override fun Element.mangaLang() = selectFirst("a:has(.g_flag)")?.attr("href")
        ?.removeSuffix("/")?.substringAfterLast("/")
        ?: selectFirst(".g_flag")?.classNames()
            ?.firstOrNull { it.startsWith("flag-") }
            ?.substringAfter("flag-")
            ?.let { langFlags[it] }
        ?: mangaLang

    private val langFlags get() = getLanguageURIs()
        .associateBy({ it.second }, { it.first })
        .toMutableMap()
        .apply {
            // Keep the existing English flag alias in case the site uses `flag-us`
            if (!containsKey("us")) {
                put("us", LANGUAGE_ENGLISH)
            }
        }

    override val popularMangaUrl get() = if (mangaLang.isBlank()) { // LANGUAGE_MULTI popular
        val popularFilter = SortOrderFilter(getSortOrderURIs()).apply { state = 0 }
        basicSearchUrl(0, "", FilterList(popularFilter))
    } else {
        super.popularMangaUrl
    }

    /* Details */
    override fun getInfoSelector(tag: String) = "li:has(.tags_text:contains($tag)) .tag .item_name"

    override fun Element.getCover() = selectFirst(".left_cover img")?.imgAttr()

    /* Filters */
    override fun tagsParser(document: Document) = document.select(".galleries .gallery_title a")
        .associate {
            it.ownText() to it.attr("href").removeSuffix("/").substringAfterLast('/')
        }

    override val mangaDetailInfoSelector = ".gallery_first"

    /* Pages */
    override val thumbnailSelector = ".gthumb"
    override val pageUri = "view"

    override fun getCategoryURIs() = listOf(
        SearchFlagFilter("Manga", "mg"),
        SearchFlagFilter("Doujinshi", "dj"),
        SearchFlagFilter("Western", "ws"),
        SearchFlagFilter("Image Set", "is"),
        SearchFlagFilter("Artist CG", "ac"),
        SearchFlagFilter("Game CG", "gc"),
    )
}
