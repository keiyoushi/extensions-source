package eu.kanade.tachiyomi.extension.all.hentaizap

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import keiyoushi.annotation.Source
import keiyoushi.network.get
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.collections.set

@Source
abstract class HentaiZap : GalleryAdults() {

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

    override val supportSpeechless = true
    override val useIntermediateSearch = true

    override fun Element.mangaThumbnail() = selectFirst(".hz-gallery-card__media.thumb img")?.imgAttr()
    override fun popularMangaSelector() = ".hz-gallery-card"

    override val mangaTitleSelector = ".hz-gallery-card__title a"

    override fun Element.mangaUrl() = selectFirst(".hz-gallery-card__cover")?.attr("abs:href")

    override fun Element.mangaLang() = select(".hz-gallery-card__flag").attr("href")
        .removeSuffix("/").substringAfterLast("/")

    override val basicSearchKey = "key"

    /* Details */
    override val mangaDetailInfoSelector = ".hz-gallery-details"

    override fun Element.getCover() = selectFirst(".hz-gallery-cover img")?.imgAttr()

    override fun getInfoSelector(tag: String) = "div.hz-gallery-entity-group:has(:contains($tag:)) a.hz-gallery-tag"

    override fun Element.infoTagName() = selectFirst(".hz-gallery-tag__name")?.text() ?: ownText()

    // pages
    override fun Element.galleryId() = select("[data-gallery-id]").attr("data-gallery-id")
    override fun Element.totalPages() = select("[data-total-pages]").attr("data-total-pages")

    override val parsingImagePageByPage = true

    // tags
    override fun tagsParser(document: Document) = document.select("ul.hz-legacy-taxonomy__items a")
        .associate {
            it.select(".hz-legacy-taxonomy-item__name").text() to it.attr("href").removeSuffix("/").substringAfterLast('/')
        }

    override val supportRelatedMangasBySearch = true
}
