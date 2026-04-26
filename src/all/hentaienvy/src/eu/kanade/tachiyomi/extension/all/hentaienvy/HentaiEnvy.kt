package eu.kanade.tachiyomi.extension.all.hentaienvy

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.Genre
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HentaiEnvy(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "HentaiEnvy",
    "https://hentaienvy.com",
    lang = lang,
) {
    override val supportsLatest = mangaLang.isNotBlank()
    override val supportAdvancedSearch = true
    override val supportSpeechless = true

    override fun Element.mangaLang() = selectFirst(".flag a")?.attr("href")
        ?.removeSuffix("/")
        ?.substringAfterLast("/")
        ?.let {
            // Include Speechless in search results
            if (it == LANGUAGE_SPEECHLESS) mangaLang else it
        } ?: mangaLang

    override fun Element.mangaTitle(selector: String): String? = mangaFullTitle(selector.takeIf { it != ".caption" } ?: ".title").let {
        if (preferences.shortTitle) it?.shortenTitle() else it
    }

    override fun Element.mangaUrl() = selectFirst("a:has(.th_img)")?.attr("abs:href")

    override fun Element.mangaThumbnail() = selectFirst("a:has(.th_img) img")?.imgAttr()

    override val basicSearchKey = "s_key"
    override val advancedSearchUri = "advanced-search"
    override val favoritePath = "inc/user.php?act=favs"

    /* Details */
    override fun Element.getInfo(tag: String): String = select("ul:has(.tag_title:contains($tag:)) a.gp_tag")
        .joinToString {
            val name = it.ownText()
            if (tag.contains(regexTag)) {
                genres[name] = it.attr("href")
                    .removeSuffix("/")
                    .substringAfterLast('/')
            }
            listOf(
                name,
                it.select(".split_tag").text()
                    .trim()
                    .removePrefix("| "),
            )
                .filter(String::isNotBlank)
                .joinToString()
        }

    override fun Element.getCover() = selectFirst(".gt_left img")?.imgAttr()

    /* Pages */
    override val thumbnailSelector = ".th_gp"

    override fun tagsParser(document: Document): List<Genre> = document.select(".tags_items a.tgl_btn")
        .mapNotNull {
            Genre(
                it.ownText(),
                it.attr("href")
                    .removeSuffix("/")
                    .substringAfterLast('/'),
            )
        }

    override fun getFilterList() = FilterList(
        listOf(
            Filter.Header("HINT: Separate search term with comma (,)"),
            Filter.Header("String query search doesn't support Sort"),
        ) + super.getFilterList().list,
    )

    override fun relatedMangaSelector() = ".related_thumbs ${popularMangaSelector()}"
}
