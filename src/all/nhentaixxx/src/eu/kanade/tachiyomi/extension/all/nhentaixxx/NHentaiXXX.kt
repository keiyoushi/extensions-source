package eu.kanade.tachiyomi.extension.all.nhentaixxx

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class NHentaiXXX : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "zh" -> LANGUAGE_CHINESE
        "all" -> LANGUAGE_MULTI
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    // This site treats all Speechless as English
    override val supportSpeechless = mangaLang == LANGUAGE_ENGLISH

    private val languages: List<Pair<String, String>> = listOf(
        Pair(LANGUAGE_ENGLISH, "1"),
        Pair(LANGUAGE_JAPANESE, "2"),
        Pair(LANGUAGE_CHINESE, "3"),
    )
    private val langCode = languages.firstOrNull { lang -> lang.first == mangaLang }?.second

    override fun Element.mangaLang() = when (attr("data-languages")) {
        langCode -> mangaLang
        else -> "other"
    }

    override fun Element.mangaUrl() = selectFirst(".gallery_item a, .fav_item a")?.attr("abs:href")

    override fun Element.mangaThumbnail() = selectFirst(".gallery_item img, .fav_item img")?.imgAttr()

    override val popularMangaUrl get() = if (mangaLang.isBlank()) { // LANGUAGE_MULTI
        val popularFilter = SortOrderFilter(getSortOrderURIs())
            .apply {
                state = 0
            }
        basicSearchUrl(0, "", FilterList(popularFilter))
    } else {
        super.popularMangaUrl
    }

    override fun popularMangaSelector() = ".galleries_box .gallery_item, .fav_item"

    override val basicSearchKey = "key"

    override val favoritePath = "favorites"

    override val idPrefixUri = "g"

    override fun loginRequired(document: Document, url: String): Boolean = (
        url.contains("/login/") &&
            document.select("button[name=go_login]").isNotEmpty()
        )

    override fun getInfoSelector(tag: String) = ".tags:contains($tag) a.tag_btn"
    override fun Element.infoTagName() = selectFirst(".tag_name")?.ownText() ?: ""

    override val serverPrefix = "i"

    override fun Element.parseJson() = selectFirst("script:containsData(parseJSON)")?.data()
        ?.substringAfter("$.parseJSON('{\"fl\":")
        ?.substringBefore(",\"th\":")?.trim()
}
