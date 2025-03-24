package eu.kanade.tachiyomi.extension.all.hentaiera

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.Genre
import eu.kanade.tachiyomi.multisrc.galleryadults.SearchFlagFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toBinary
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HentaiEra(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "HentaiEra",
    "https://hentaiera.com",
    lang = lang,
) {
    override val supportsLatest = true
    override val useIntermediateSearch: Boolean = true
    override val supportSpeechless: Boolean = true

    override fun Element.mangaTitle(selector: String): String? =
        mangaFullTitle(selector.replace("caption", "gallery_title")).let {
            if (preferences.shortTitle) it?.shortenTitle() else it
        }

    override fun Element.mangaLang() =
        select("a:has(.g_flag)").attr("href")
            .removeSuffix("/").substringAfterLast("/")
            .let {
                // Include Speechless in search results
                if (it == LANGUAGE_SPEECHLESS) mangaLang else it
            }

    override fun popularMangaRequest(page: Int): Request {
        // Only for query string or multiple tags
        val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            addQueryParameter("pp", "1")

            getLanguageURIs().forEach { pair ->
                addQueryParameter(
                    pair.second,
                    toBinary(mangaLang == pair.first || mangaLang == LANGUAGE_MULTI),
                )
            }

            addPageUri(page)
        }

        return GET(url.build(), headers)
    }

    /* Details */
    override fun Element.getInfo(tag: String): String {
        return select("li:has(.tags_text:contains($tag)) .tag .item_name")
            .joinToString {
                val name = it.ownText()
                if (tag.contains(regexTag)) {
                    genres[name] = it.parent()!!.attr("href")
                        .removeSuffix("/").substringAfterLast('/')
                }
                listOf(
                    name,
                    it.select(".split_tag").text()
                        .trim()
                        .removePrefix("| "),
                )
                    .filter { s -> s.isNotBlank() }
                    .joinToString()
            }
    }

    override fun Element.getCover() =
        selectFirst(".left_cover img")?.imgAttr()

    override fun tagsParser(document: Document): List<Genre> {
        return document.select("h2.gallery_title a")
            .mapNotNull {
                Genre(
                    it.text(),
                    it.attr("href")
                        .removeSuffix("/").substringAfterLast('/'),
                )
            }
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
