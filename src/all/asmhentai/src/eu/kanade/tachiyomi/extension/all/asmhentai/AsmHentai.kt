package eu.kanade.tachiyomi.extension.all.asmhentai

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.Genre
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AsmHentai(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "AsmHentai",
    "https://asmhentai.com",
    lang = lang,
) {
    override val supportsLatest = mangaLang.isNotBlank()
    override val supportSpeechless: Boolean = true

    override fun Element.mangaLang() =
        select("a:has(.flag)").attr("href")
            .removeSuffix("/").substringAfterLast("/")
            .let {
                // Include Speechless in search results
                if (it == LANGUAGE_SPEECHLESS) mangaLang else it
            }

    override fun Element.mangaUrl() =
        selectFirst(".image a")?.attr("abs:href")

    override fun Element.mangaThumbnail() =
        selectFirst(".image img")?.imgAttr()

    override fun popularMangaSelector() = ".preview_item"

    override val favoritePath = "inc/user.php?act=favs"

    override fun Element.getInfo(tag: String): String {
        return select(".tags:contains($tag:) .tag_list a")
            .joinToString {
                val name = it.selectFirst(".tag")?.ownText() ?: ""
                if (tag.contains(regexTag)) {
                    genres[name] = it.attr("href")
                        .removeSuffix("/").substringAfterLast('/')
                }
                listOf(
                    name,
                    it.select(".split_tag").text()
                        .removePrefix("| ")
                        .trim(),
                )
                    .filter { s -> s.isNotBlank() }
                    .joinToString()
            }
    }

    override fun Element.getInfoPages(document: Document?) =
        selectFirst(".book_page .pages h3")?.ownText()

    override val mangaDetailInfoSelector = ".book_page"

    /**
     * [totalPagesSelector] only exists if pages > 10
     */
    override val totalPagesSelector = "t_pages"

    override val galleryIdSelector = "load_id"
    override val thumbnailSelector = ".preview_thumb"

    override val idPrefixUri = "g"
    override val pageUri = "gallery"

    override fun pageRequestForm(document: Document, totalPages: String, loadedPages: Int): FormBody {
        val token = document.select("[name=csrf-token]").attr("content")

        return FormBody.Builder()
            .add("id", document.inputIdValueOf(loadIdSelector))
            .add("dir", document.inputIdValueOf(loadDirSelector))
            .add("visible_pages", loadedPages.toString())
            .add("t_pages", totalPages)
            .add("type", "2") // 1 would be "more", 2 is "all remaining"
            .apply {
                if (token.isNotBlank()) add("_token", token)
            }
            .build()
    }

    override fun tagsParser(document: Document): List<Genre> {
        return document.select(".tags_page .tags a.tag")
            .mapNotNull {
                Genre(
                    it.ownText(),
                    it.attr("href")
                        .removeSuffix("/").substringAfterLast('/'),
                )
            }
    }

    override fun getFilterList() = FilterList(
        listOf(
            Filter.Header("HINT: Separate search term with comma (,)"),
            Filter.Header("String query search doesn't support Sort"),
        ) + super.getFilterList().list,
    )
}
