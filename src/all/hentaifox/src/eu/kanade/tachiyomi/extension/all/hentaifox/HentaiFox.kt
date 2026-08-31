package eu.kanade.tachiyomi.extension.all.hentaifox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toDate
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiFox : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "zh" -> LANGUAGE_CHINESE
        "ko" -> LANGUAGE_KOREAN
        "all" -> LANGUAGE_MULTI
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    override val supportsLatest = mangaLang.isNotBlank()

    override val xhrHeaders get() = headersBuilder().apply {
        csrfToken?.let { add("X-Csrf-Token", it) }
        add("X-Requested-With", "XMLHttpRequest")
    }
        .build()

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

    override fun getInfoSelector(tag: String) = "ul.${tag.lowercase()} a"

    override fun Element.getTime(): Long = selectFirst(".pages:contains(Posted:)")?.ownText()
        ?.removePrefix("Posted: ")
        .toDate(null)

    override fun String.addPageUri(page: Int) = buildString {
        append(this@addPageUri)
        when {
            equals("$baseUrl/") && page == 2 -> append("page/$page/")
            contains('?') -> append("&page=$page")
            else -> append("/pag/$page/")
        }
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

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            Filter.Header("HINT: Use double quote (\") for exact match"),
        ) + super.getFilterList(data).list,
    )

    private val sidebarPath = "includes/sidebar.php"

    private fun sidebarMangaSelector() = "div.item"

    private fun Element.sidebarMangaTitle() = selectFirst("img")?.attr("alt")

    private fun Element.sidebarMangaUrl() = selectFirst("a")?.attr("abs:href")

    private fun Element.sidebarMangaThumbnail() = selectFirst("img")?.imgAttr()

    private var csrfToken: String? = null

    private fun Document.storeCsrf() {
        csrfToken = select("[name=csrf-token]").attr("content")
    }

    override fun parsePopularManga(document: Document) = super.parsePopularManga(document.also { it.storeCsrf() })

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // Sidebar mangas should always override any other search, so they should appear first
        // and only propagate to super when a "normal" search is issued
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()

        sortOrderFilter?.let {
            val selectedCategory = sortOrderFilter.values[sortOrderFilter.state]
            if (sidebarCategoriesFilterStateMap.containsKey(selectedCategory)) {
                return parseSearchManga(
                    getSidebar(
                        sidebarCategoriesFilterStateMap.getValue(selectedCategory),
                    ),
                )
            }
        }

        return super.getSearchMangaList(page, query, filters)
    }

    private suspend fun getSidebar(category: String): Response {
        val url = "$baseUrl/$sidebarPath"
        return client.post(
            url,
            xhrHeaders,
            FormBody.Builder()
                .add("type", category)
                .build(),
        )
    }

    override fun parseSearchManga(response: Response): MangasPage {
        if (response.request.url.encodedPath.endsWith(sidebarPath)) {
            val document = response.asJsoup()

            val mangas = document.select(sidebarMangaSelector())
                .mapNotNull {
                    SMangaDto(
                        title = it.sidebarMangaTitle() ?: return@mapNotNull null,
                        url = it.sidebarMangaUrl() ?: return@mapNotNull null,
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
            return super.parseSearchManga(response)
        }
    }

    override fun getSortOrderURIs(): List<Pair<String, String>> = super.getSortOrderURIs() + sidebarCategoriesFilterStateMap.toList()

    companion object {
        private val sidebarCategoriesFilterStateMap = mapOf(
            "Top Rated" to "top_rated",
            "Most Faved" to "top_faved",
            "Most Fapped" to "top_fapped",
            "Most Downloaded" to "top_downloaded",
        ).withDefault { "top_rated" }
    }
}
