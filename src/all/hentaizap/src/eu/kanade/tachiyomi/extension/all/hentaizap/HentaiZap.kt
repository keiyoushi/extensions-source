package eu.kanade.tachiyomi.extension.all.hentaizap

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.Page
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

    override fun popularMangaNextPageSelector() = "a[rel='next']"
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

    override fun Element.getInfoPages(document: Document?) = document?.selectFirst(".hz-gallery-pages")?.ownText()

    override fun getInfoSelector(tag: String) = "div.hz-gallery-entity-group:has(span.hz-gallery-entity-label:contains($tag:)) a.hz-gallery-tag"

    override fun Element.infoTagName() = selectFirst(".hz-gallery-tag__name")?.text() ?: ownText()

    // pages
    override suspend fun pageListParse(document: Document): List<Page> {
        val totalPages = "data-total-pages"
        val total = document.select("[$totalPages]").attr("$totalPages").toIntOrNull() ?: 0
        val galleryId = document.location().trim('/').substringAfterLast('/')

        return (1..total).map { idx ->
            Page(idx, "$baseUrl/$pageUri/$galleryId/$idx")
        }
    }

    override fun imageUrlParse(document: Document) = document.selectFirst("#readerImg")?.imgAttr().orEmpty()

    // tags
    override fun tagsParser(document: Document) = document.select("ul.hz-legacy-taxonomy__items a")
        .associate {
            it.ownText() to it.attr("href").removeSuffix("/").substringAfterLast('/')
        }

    override val supportsFilterFetching = false
    override val supportRelatedMangasBySearch = true
}
