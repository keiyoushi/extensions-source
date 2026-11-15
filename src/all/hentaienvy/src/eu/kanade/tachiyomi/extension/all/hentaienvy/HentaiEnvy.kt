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

    override fun Element.mangaLang() = select(".hnv-gallery-card__flag").attr("href")
        .removeSuffix("/").substringAfterLast("/")

    override fun popularMangaSelector() = ".hnv-gallery-card"

    override fun Element.mangaThumbnail() = selectFirst("a.hnv-gallery-card__cover img")?.imgAttr()

    override val mangaTitleSelector = ".hnv-gallery-card__title"

    override fun Element.mangaUrl() = selectFirst("a.hnv-gallery-card__cover")?.attr("abs:href")

    override val basicSearchKey = "key"
    override val advancedSearchUri = "advanced-search"
    override val favoritePath = "inc/user.php?act=favs"

    /* Details */
    override val mangaDetailInfoSelector = ".hnv-gallery-details"

    override fun getInfoSelector(tag: String) = "div.hnv-gallery-entity-group:has(:contains($tag:)) a.hnv-gallery-tag"
    override fun Element.infoTagName() = selectFirst(".hnv-gallery-tag__name")?.text() ?: ownText()

    override fun Element.getCover() = selectFirst(".hnv-gallery-cover img")?.imgAttr()

    /* Pages */
    override fun Element.galleryId() = select("[data-gallery-id]").attr("data-gallery-id")
    override fun Element.totalPages() = select("[data-total-pages]").attr("data-total-pages")

    override val parsingImagePageByPage = true

    /* Filters */
    override fun tagsParser(document: Document) = document.select("ul.hnv-legacy-taxonomy__items a")
        .associate {
            it.attr("title") to it.attr("href").removeSuffix("/").substringAfterLast('/')
        }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            Filter.Header("String query search doesn't support Sort"),
        ) + super.getFilterList(data).list,
    )

    override val supportRelatedMangasBySearch = true
}
