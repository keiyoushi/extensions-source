package eu.kanade.tachiyomi.extension.all.hentaiclap

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.GenresFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.RandomEntryFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toDate
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiClap : GalleryAdults() {

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

    // Only the language pages can be sorted by popularity
    override val supportsLatest = mangaLang.isNotBlank()

    override val supportSpeechless = true

    override val basicSearchKey = "key"

    /* List */

    override val mangaTitleSelector = "h2"

    override fun Element.mangaLang() = selectFirst(".g_flg a")?.attr("href").orEmpty()
        .removeSuffix("/").substringAfterLast("/")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenresFilter>()?.selected.orEmpty()
        if (query.isNotBlank() || genres.size != 1 || filters.any { it is Filter.CheckBox && it.state }) {
            return super.getSearchMangaList(page, query, filters)
        }
        // Tag pages answer with a 404 status, along with their galleries
        val url = tagBrowsingSearchUrl(page, query, filters).addPageUri(page)
        return parseSearchManga(client.get(url, Fetch.DOCUMENT, ensureSuccess = false))
    }

    override suspend fun searchFavoriteFilter(page: Int, query: String, filters: FilterList) = parseSearchManga(
        client.get("$baseUrl/favorites/".addPageUri(page), Fetch.DOCUMENT),
    )

    /* Details */

    // Gallery paths need their trailing slash
    override suspend fun getMangaById(id: String): SManga = parseMangaDetails(client.get("$baseUrl/$idPrefixUri/$id/", Fetch.DOCUMENT).asJsoup())
        .apply { url = "/$idPrefixUri/$id/" }

    override fun Element.getCover() = selectFirst(".gt_cover img")?.imgAttr()

    override fun getInfoSelector(tag: String) = "ul:has(> .ri_txt:contains($tag)) a"

    override fun Element.getInfoPages(document: Document?) = selectFirst(".pages")?.ownText()?.removePrefix("Pages ")

    override fun Element.getTime() = selectFirst(".added")?.ownText()?.removePrefix("Uploaded ").toDate(null)

    /* Pages */

    override val pageUri = "read"
    override val loadDirSelector = "image_dir"
    override val loadIdSelector = "gallery_id"
    override val serverSelector = "server_id"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.trimEnd('/').substringAfterLast('/')
        val document = client.get("$baseUrl/$pageUri/$id/1/", Fetch.DOCUMENT).asJsoup()
        val imagesUri = "https://${document.getServer()}/${document.inputIdValueOf(loadDirSelector)}/${document.inputIdValueOf(loadIdSelector)}"
        // The reader lists each page's image type and size
        val images = document.selectFirst("script:containsData(var g_th)")!!.data()
            .substringAfter("var g_th = ")
            .substringBefore(";")
            .parseAs<Map<String, String>>()
        return images.entries.sortedBy { it.key.toInt() }.mapIndexed { index, (number, info) ->
            Page(index, imageUrl = "$imagesUri/$number.${IMAGE_TYPES[info.substringBefore(',')] ?: "jpg"}")
        }
    }

    /* Filters */

    override fun tagsParser(document: Document) = document.select("div.tag > a:has(h2)")
        .associate { it.text() to it.attr("href").removeSuffix("/").substringAfterLast('/') }

    // The site's random page comes back empty
    override fun getFilterList(data: JsonElement?) = FilterList(super.getFilterList(data).filterNot { it is RandomEntryFilter })

    companion object {
        private val IMAGE_TYPES = mapOf("j" to "jpg", "p" to "png", "b" to "bmp", "g" to "gif", "w" to "webp")
    }
}
