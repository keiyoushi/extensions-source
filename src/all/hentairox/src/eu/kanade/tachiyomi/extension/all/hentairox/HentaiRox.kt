package eu.kanade.tachiyomi.extension.all.hentairox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiRox : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "all" -> LANGUAGE_MULTI
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    override val supportSpeechless = true

    override val basicSearchKey = "key"

    /* List */

    override fun popularMangaSelector() = "article.hr-gallery-card"

    override val mangaTitleSelector = ".hr-gallery-card__title"

    override fun Element.mangaUrl() = selectFirst(".hr-gallery-card__cover")?.attr("abs:href")

    override fun Element.mangaThumbnail() = selectFirst(".hr-gallery-card__media img")?.imgAttr()

    override fun Element.mangaLang() = selectFirst(".hr-gallery-card__flag")?.attr("href")
        .orEmpty()
        .removeSuffix("/")
        .substringAfterLast("/")

    // The site has no language-independent popular/latest listing, so the search
    // endpoint (sorted by popularity or by newest) covers all languages instead.
    override val popularMangaUrl get() = if (mangaLang.isBlank()) {
        "$baseUrl/search/?sort=popular"
    } else {
        super.popularMangaUrl
    }

    override val latestUpdatesUrl get() = if (mangaLang.isBlank()) {
        "$baseUrl/search/"
    } else {
        super.latestUpdatesUrl
    }

    /* Details */

    override val mangaDetailInfoSelector = ".hr-gallery-overview"

    override fun Element.getCover() = selectFirst(".hr-gallery-cover img")?.imgAttr()

    override fun getInfoSelector(tag: String) = ".hr-gallery-entity-group:has(.hr-gallery-entity-label:contains($tag)) .hr-gallery-tag"

    override fun Element.infoTagName() = selectFirst(".hr-gallery-tag__name")?.text().orEmpty()

    override fun Element.totalPages() = selectFirst("#js-thumbs-grid")?.attr("data-total-pages").orEmpty()

    /* Related */

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val id = manga.url.trimEnd('/').substringAfterLast('/')
        val html = client.get("$baseUrl/api/gallery/$id/related", xhrHeaders)
            .parseAs<RelatedResponse>()
            .html

        return html.asJsoup(baseUrl)
            .select(relatedMangaSelector())
            .searchMangaFromElements(hasNextPage = false)
    }

    /* Pages */

    override val pageUri = "g"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val galleryId = chapter.url.trimEnd('/').substringAfterLast('/')
        val document = client.get("$baseUrl/$pageUri/$galleryId/1/").asJsoup()

        val pagesJson = document.selectFirst("script#readerPagesJson")?.data()
            ?: error("Failed to load page list")
        val imagesUri = document.selectFirst("img#readerImg")?.imgAttr()?.substringBeforeLast('/')
            ?: error("Failed to load page list")

        return pagesJson.parseAs<List<ReaderPage>>().map {
            Page(
                index = it.page - 1,
                url = "$baseUrl/$pageUri/$galleryId/${it.page}/",
                imageUrl = "$imagesUri/${it.page}.${it.ext}",
            )
        }
    }

    /* Filters */

    override fun tagsParser(document: Document) = document.select("article.hr-taxonomy-card")
        .mapNotNull { it.selectFirst(".hr-taxonomy-card__title a") }
        .associate {
            it.text() to it.attr("href").removeSuffix("/").substringAfterLast('/')
        }

    @Serializable
    private class RelatedResponse(val html: String)

    @Serializable
    private class ReaderPage(val page: Int, val ext: String)
}
