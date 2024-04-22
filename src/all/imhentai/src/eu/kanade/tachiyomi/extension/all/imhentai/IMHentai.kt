package eu.kanade.tachiyomi.extension.all.imhentai

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.cleanTag
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.imgAttr
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

class IMHentai(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "IMHentai",
    "https://imhentai.xxx",
    lang = lang,
) {
    override val supportsLatest = true

    private val SharedPreferences.shortTitle
        get() = getBoolean(PREF_SHORT_TITLE, false)

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")

    private fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHORT_TITLE
            title = "Display Short Titles"
            summaryOff = "Showing Long Titles"
            summaryOn = "Showing short Titles"
            setDefaultValue(false)
        }.also(screen::addPreference)

        super.setupPreferenceScreen(screen)
    }

    override fun Element.mangaTitle(selector: String) =
        mangaFullTitle(selector).let {
            if (preferences.shortTitle) it?.shortenTitle() else it
        }

    private fun Element.mangaFullTitle(selector: String) =
        selectFirst(selector)?.text()
            ?.replace("\"", "")?.trim()

    override fun Element.mangaLang() =
        select("a:has(.thumb_flag)").attr("href")
            .removeSuffix("/").substringAfterLast("/")
            .let {
                // Include Speechless in search results
                if (it == LANGUAGE_SPEECHLESS) mangaLang else it
            }

    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .removeAll("Accept-Encoding")
                .build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .addInterceptor(
            fun(chain): Response {
                val response = chain.proceed(chain.request())
                if (!response.headers("Content-Type").toString().contains("text/html")) return response

                val responseContentType = response.body.contentType()
                val responseString = response.body.string()

                if (responseString.contains("Overload... Please use the advanced search")) {
                    response.close()
                    throw IOException("IMHentai search is overloaded try again later")
                }

                return response.newBuilder()
                    .body(responseString.toResponseBody(responseContentType))
                    .build()
            },
        ).build()

    private fun toBinary(boolean: Boolean) = if (boolean) "1" else "0"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortOrderFilter = filters.filterIsInstance<SortOrderFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()
        val favoriteFilter = filters.filterIsInstance<FavoriteFilter>().firstOrNull()
        val speechlessFilter = filters.filterIsInstance<SpeechlessFilter>().firstOrNull()
        return when {
            favoriteFilter?.state == true -> {
                val url = "$baseUrl/$favoritePath".toHttpUrl().newBuilder()
                return POST(
                    url.build().toString(),
                    xhrHeaders,
                    FormBody.Builder()
                        .add("page", page.toString())
                        .build(),
                )
            }
            speechlessFilter?.state == true -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("language")
                    addPathSegment(GalleryAdults.LANGUAGE_SPEECHLESS)
                    if (sortOrderFilter?.state == 0) addPathSegment("popular")
                    addPageUri(page)
                }
                GET(url.build(), headers)
            }
                    }
                    }
                        }
            }
            selectedGenres.size == 1 && query.isBlank() -> {
                // Browsing single tag's catalog
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("tag")
                    addPathSegment(selectedGenres.single().uri)
                    if (sortOrderFilter?.state == 0) addPathSegment("popular")
                    addPageUri(page)
                }
                GET(url.build(), headers)
            }
                    }
                }
            }
        }
    }

    /* Details */
    override val idPrefixUri = "gallery"

    override fun Element.getInfo(tag: String): String {
        return select("li:has(.tags_text:contains($tag:)) .tag").map {
            it?.run {
                listOf(
                    ownText().cleanTag(),
                    select(".split_tag").text()
                        .trim()
                        .removePrefix("| ")
                        .cleanTag(),
                )
                    .filter { s -> s.isNotBlank() }
                    .joinToString()
            }
        }.joinToString()
    }

    override fun Element.getDescription(): String {
        return (
            listOf("Parodies", "Characters", "Languages", "Category")
                .mapNotNull { tag ->
                    getInfo(tag)
                        .let { if (it.isNotBlank()) "$tag: $it" else null }
                } +
                listOfNotNull(
                    selectFirst(".pages")?.ownText()?.cleanTag(),
                    selectFirst(".subtitle")?.ownText()?.cleanTag()
                        .let { altTitle -> if (!altTitle.isNullOrBlank()) "Alternate Title: $altTitle" else null },
                )
            )
            .joinToString("\n\n")
            .plus(
                if (preferences.shortTitle) {
                    "\nFull title: ${mangaFullTitle("h1")}"
                } else {
                    ""
                },
            )
    }

    override fun Element.getCover() =
        selectFirst(".left_cover img")?.imgAttr()

    override val mangaDetailInfoSelector = ".gallery_first"

    /* Pages */
    override val pageUri = "view"
    override val pageSelector = ".gthumb"
    private val serverSelector = "load_server"

    private fun serverNumber(document: Document, galleryId: String): String {
        return document.inputIdValueOf(serverSelector).takeIf {
            it.isNotBlank()
        } ?: when (galleryId.toInt()) {
            in 1..274825 -> "1"
            in 274826..403818 -> "2"
            in 403819..527143 -> "3"
            in 527144..632481 -> "4"
            in 632482..816010 -> "5"
            in 816011..970098 -> "6"
            in 970099..1121113 -> "7"
            else -> "8"
        }
    }

    override fun getServer(document: Document, galleryId: String): String {
        val domain = baseUrl.toHttpUrl().host
        return "m${serverNumber(document, galleryId)}.$domain"
    }

    override fun pageRequestForm(document: Document, totalPages: String): FormBody {
        val galleryId = document.inputIdValueOf(galleryIdSelector)

        return FormBody.Builder()
            .add("server", serverNumber(document, galleryId))
            .add("u_id", document.inputIdValueOf(galleryIdSelector))
            .add("g_id", document.inputIdValueOf(loadIdSelector))
            .add("img_dir", document.inputIdValueOf(loadDirSelector))
            .add("visible_pages", "10")
            .add("total_pages", totalPages)
            .add("type", "2") // 1 would be "more", 2 is "all remaining"
            .build()
    }

    /* Filters */
    override fun tagsParser(document: Document): List<Pair<String, String>> {
        return document.select(".stags .tag_btn")
            .mapNotNull {
                Pair(
                    it.selectFirst(".list_tag")?.ownText() ?: "",
                    it.select("a").attr("href")
                        .removeSuffix("/").substringAfterLast('/'),
                )
            }
    }

    private class SortOrderFilter(sortOrderURIs: List<Pair<String, String>>) :
        Filter.Select<String>("Sort By", sortOrderURIs.map { it.first }.toTypedArray())

    private open class SearchFlagFilter(name: String, val uri: String, state: Boolean = true) : Filter.CheckBox(name, state)
    private class LanguageFilter(name: String, uri: String = name) : SearchFlagFilter(name, uri, false)
    private class LanguageFilters(flags: List<LanguageFilter>) : Filter.Group<LanguageFilter>("Other Languages", flags)
    private class CategoryFilters(flags: List<SearchFlagFilter>) : Filter.Group<SearchFlagFilter>("Categories", flags)

    override fun getFilterList() = FilterList(
        super.getFilterList().list +
            getFilterList(SORT_ORDER_DEFAULT).list,
    )

    private fun getFilterList(sortOrderState: Int) = FilterList(
        SortOrderFilter(getSortOrderURIs(), sortOrderState),
        CategoryFilters(getCategoryURIs()),
        LanguageFilters(getLanguageURIs().filter { it.name != mangaLang }), // exclude main lang
        Filter.Header("Speechless language: ignores all filters except \"Popular\" and \"Latest\" in Sorting Filter"),
    )
    private class SpeechlessFilter : Filter.CheckBox("Show speechless items only", false)

    override fun getFilterList(): FilterList {
        getGenres()
        return FilterList(
            if (genres.isEmpty()) {
                Filter.Header("Press 'reset' to attempt to load tags")
            } else {
                GenresFilter(genres)
            },
            Filter.Separator(),

            SortOrderFilter(getSortOrderURIs()),
            CategoryFilters(getCategoryURIs()),

            Filter.Separator(),
            SpeechlessFilter(),
            FavoriteFilter(),
        )
    }

    private fun getCategoryURIs() = listOf(
        SearchFlagFilter("Manga", "manga"),
        SearchFlagFilter("Doujinshi", "doujinshi"),
        SearchFlagFilter("Western", "western"),
        SearchFlagFilter("Image Set", "imageset"),
        SearchFlagFilter("Artist CG", "artistcg"),
        SearchFlagFilter("Game CG", "gamecg"),
    )

    // update sort order indices in companion object if order is changed
    private fun getSortOrderURIs() = listOf(
        Pair("Popular", "pp"),
        Pair("Latest", "lt"),
        Pair("Downloads", "dl"),
        Pair("Top Rated", "tr"),
    )

    private fun getLanguageURIs() = listOf(
        Pair(LANGUAGE_ENGLISH, "en"),
        Pair(LANGUAGE_JAPANESE, "jp"),
        Pair(LANGUAGE_SPANISH, "es"),
        Pair(LANGUAGE_FRENCH, "fr"),
        Pair(LANGUAGE_KOREAN, "kr"),
        Pair(LANGUAGE_GERMAN, "de"),
        Pair(LANGUAGE_RUSSIAN, "ru"),
    )

    private fun getLanguageURIByName(name: String): LanguageFilter {
        return getLanguageURIs().first { it.name == name }
    }

    override val idPrefixUri = "gallery"

    companion object {

        // references to sort order indices
        private const val SORT_ORDER_POPULAR = 0
        private const val SORT_ORDER_LATEST = 1
        private const val SORT_ORDER_DEFAULT = SORT_ORDER_POPULAR

        // references to be used in factory
        const val LANGUAGE_ENGLISH = "English"
        const val LANGUAGE_JAPANESE = "Japanese"
        const val LANGUAGE_SPANISH = "Spanish"
        const val LANGUAGE_FRENCH = "French"
        const val LANGUAGE_KOREAN = "Korean"
        const val LANGUAGE_GERMAN = "German"
        const val LANGUAGE_RUSSIAN = "Russian"
        const val LANGUAGE_SPEECHLESS = "Speechless"

        private const val PREF_SHORT_TITLE = "pref_short_title"
    }
}
