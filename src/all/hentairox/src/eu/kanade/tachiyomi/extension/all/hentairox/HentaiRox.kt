package eu.kanade.tachiyomi.extension.all.hentairox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiRox : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "zh" -> LANGUAGE_CHINESE
        "all" -> LANGUAGE_MULTI
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    override val supportSpeechless = true

    override val basicSearchKey = "key"

    override fun Element.mangaLang() = select("a:has(.thumb_flag)").attr("href")
        .removeSuffix("/").substringAfterLast("/")

    override val mangaTitleSelector = ".gallery_title"

    override val popularMangaUrl = if (mangaLang.isBlank()) { // LANGUAGE_MULTI
        "$baseUrl/top-rated"
    } else {
        super.popularMangaUrl
    }

    /**
     * Convert space( ) typed in search-box into plus(+) in URL. Then:
     * - ignore the word preceding by a special character (e.g. 'school-girl' will ignore 'girl')
     *    => replace to plus(+),
     * - use plus(+) for separate terms, as AND condition.
     * - use double quote(") to search for exact match.
     */
    override fun buildQueryString(tags: List<String>, query: String): String {
        val regexSpecialCharacters = Regex("""[^a-zA-Z0-9"]+(?=[a-zA-Z0-9"])""")
        return (tags + query + mangaLang).filterNot { it.isBlank() }.joinToString("+") {
            it.trim().replace(regexSpecialCharacters, "+")
        }
    }

    /* Details */
    override fun getInfoSelector(tag: String) = "li:has(.tags_text:contains($tag)) a.tag"
    override fun Element.infoTagName() = selectFirst(".item_name")?.ownText() ?: ""

    override fun Element.getCover() = selectFirst(".left_cover img")?.imgAttr()

    override val mangaDetailInfoSelector = ".gallery_first"

    /* Pages */
    override val thumbnailSelector = ".gthumb"
    override val pageUri = "view"

    /* Filters */
    override fun tagsParser(document: Document) = document.select(".gtags .gallery_title a")
        .associate {
            it.ownText() to
                it.attr("href")
                    .removeSuffix("/").substringAfterLast('/')
        }

    override fun relatedMangaSelector() = ".related_galleries ~ ${popularMangaSelector()}"
}
