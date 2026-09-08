package eu.kanade.tachiyomi.extension.all.hentaienvy

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiEnvy : GalleryAdults() {

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

    override val supportsLatest get() = mangaLang.isNotBlank()
    override val supportAdvancedSearch = true
    override val supportSpeechless = true

    override fun Element.mangaLang() = selectFirst(".flag a")?.attr("href")
        ?.removeSuffix("/")
        ?.substringAfterLast("/") ?: mangaLang

    override val mangaTitleSelector = ".title"

    override fun Element.mangaUrl() = selectFirst("a:has(.th_img)")?.attr("abs:href")

    override fun Element.mangaThumbnail() = selectFirst("a:has(.th_img) img")?.imgAttr()

    override val basicSearchKey = "s_key"
    override val advancedSearchUri = "advanced-search"
    override val favoritePath = "inc/user.php?act=favs"

    /* Details */
    override fun getInfoSelector(tag: String) = "ul:has(.tag_title:contains($tag:)) a.gp_tag"

    override fun Element.getCover() = selectFirst(".gt_left img")?.imgAttr()

    /* Pages */
    override val thumbnailSelector = ".th_gp"

    override fun tagsParser(document: Document) = document.select(".tags_items a.tgl_btn")
        .associate {
            it.ownText() to it.attr("href").removeSuffix("/").substringAfterLast('/')
        }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            Filter.Header("String query search doesn't support Sort"),
        ) + super.getFilterList(data).list,
    )

    override fun relatedMangaSelector() = ".related_thumbs ${popularMangaSelector()}"
}
