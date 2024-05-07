package eu.kanade.tachiyomi.extension.all.hentaifox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.toDate
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
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
}
