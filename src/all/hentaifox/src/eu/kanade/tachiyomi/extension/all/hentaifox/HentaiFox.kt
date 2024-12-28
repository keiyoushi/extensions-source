package eu.kanade.tachiyomi.extension.all.hentaifox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.Genre
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toDate
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HentaiFox(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "HentaiFox",
    "https://hentaifox.com",
    lang = lang,
) {
    override val supportsLatest = mangaLang.isNotBlank()

    private val languages: List<Pair<String, String>> = listOf(
        Pair(LANGUAGE_ENGLISH, "1"),
        Pair(LANGUAGE_TRANSLATED, "2"),
        Pair(LANGUAGE_JAPANESE, "5"),
        Pair(LANGUAGE_CHINESE, "6"),
        Pair(LANGUAGE_KOREAN, "11"),
    )
    private val langCode = languages.firstOrNull { lang -> lang.first == mangaLang }?.second

    override fun Element.mangaLang() = attr("data-languages")
        .split(' ').let {
            when {
                it.contains(langCode) -> mangaLang
                // search result doesn't have "data-languages" which will return a list with 1 blank element
                it.size > 1 || (it.size == 1 && it.first().isNotBlank()) -> "other"
                // if we don't know which language to filter then set to mangaLang to not filter at all
                else -> mangaLang
            }
        }

    override val useShortTitlePreference = false
    override fun Element.mangaTitle(selector: String): String? = mangaFullTitle(selector)

    override fun Element.getInfo(tag: String): String {
        return select("ul.${tag.lowercase()} a")
            .joinToString {
                val name = it.ownText()
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

    override fun Element.getTime(): Long =
        selectFirst(".pages:contains(Posted:)")?.ownText()
            ?.removePrefix("Posted: ")
            .toDate(simpleDateFormat)

    override fun HttpUrl.Builder.addPageUri(page: Int): HttpUrl.Builder {
        val url = toString()
        when {
            url == "$baseUrl/" && page == 2 ->
                addPathSegments("page/$page")
            url.contains('?') ->
                addQueryParameter("page", page.toString())
            else ->
                addPathSegments("pag/$page")
        }
        addPathSegment("") // trailing slash (/)
        return this
    }

    /**
     * Convert space( ) typed in search-box into plus(+) in URL. Then:
     * - ignore the word preceding by a special character (e.g. 'school-girl' will ignore 'girl')
     *    => replace to plus(+),
     * - use plus(+) for separate terms, as AND condition.
     * - use double quote(") to search for exact match.
     */
    override fun buildQueryString(tags: List<String>, query: String): String {
        val regexSpecialCharacters = Regex("""[^a-zA-Z0-9"]+(?=[a-zA-Z0-9"])""")
        return (tags + query + mangaLang).filterNot { it.isBlank() }.joinToString("+") {
            it.trim().replace(regexSpecialCharacters, "+")
        }
    }

    override val favoritePath = "includes/user_favs.php"
    override val pagesRequest = "includes/thumbs_loader.php"

    override fun getFilterList() = FilterList(
        listOf(
            Filter.Header("HINT: Use double quote (\") for exact match"),
        ) + super.getFilterList().list,
    )

    private val sidebarPath = "includes/sidebar.php"

    private fun sidebarMangaSelector() = "div.item"

    private fun Element.sidebarMangaTitle() =
        selectFirst("img")?.attr("alt")

    private fun Element.sidebarMangaUrl() =
        selectFirst("a")?.attr("abs:href")

    private fun Element.sidebarMangaThumbnail() =
        selectFirst("img")?.imgAttr()

    private var csrfToken: String? = null

    override fun tagsParser(document: Document): List<Genre> {
        csrfToken = csrfParser(document)
        return super.tagsParser(document)
    }

    private fun csrfParser(document: Document): String {
        return document.select("[name=csrf-token]").attr("content")
    }

    private fun setSidebarHeaders(csrfToken: String?): Headers {
        if (csrfToken == null) {
            return xhrHeaders
        }
        return xhrHeaders.newBuilder()
            .add("X-Csrf-Token", csrfToken)
            .build()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Sidebar mangas should always override any other search, so they should appear first
        // and only propagate to super when a "normal" search is issued
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()

        sortOrderFilter?.let {
            val selectedCategory = sortOrderFilter.values.get(sortOrderFilter.state)
            if (sidebarCategoriesFilterStateMap.containsKey(selectedCategory)) {
                return sidebarRequest(
                    sidebarCategoriesFilterStateMap.getValue(selectedCategory),
                )
            }
        }
        return super.searchMangaRequest(page, query, filters)
    }

    private fun sidebarRequest(category: String): Request {
        val url = "$baseUrl/$sidebarPath"
        return POST(
            url,
            setSidebarHeaders(csrfToken),
            FormBody.Builder()
                .add("type", category)
                .build(),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath.endsWith(sidebarPath)) {
            val document = response.asJsoup()
            val mangas = document.select(sidebarMangaSelector())
                .map {
                    SMangaDto(
                        title = it.sidebarMangaTitle()!!,
                        url = it.sidebarMangaUrl()!!,
                        thumbnail = it.sidebarMangaThumbnail(),
                        lang = LANGUAGE_MULTI,
                    )
                }
                .map {
                    SManga.create().apply {
                        title = it.title
                        setUrlWithoutDomain(it.url)
                        thumbnail_url = it.thumbnail
                    }
                }

            return MangasPage(mangas, false)
        } else {
            return super.searchMangaParse(response)
        }
    }

    override fun getSortOrderURIs(): List<Pair<String, String>> {
        return super.getSortOrderURIs() + sidebarCategoriesFilterStateMap.toList()
    }

    companion object {
        private val sidebarCategoriesFilterStateMap = mapOf(
            "Top Rated" to "top_rated",
            "Most Faved" to "top_faved",
            "Most Fapped" to "top_fapped",
            "Most Downloaded" to "top_downloaded",
        ).withDefault { "top_rated" }
    }
}
