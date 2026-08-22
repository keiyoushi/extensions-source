package eu.kanade.tachiyomi.extension.all.nhentaito

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toDate
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class NHentaiTo : GalleryAdults() {

    override val mangaLang = when (lang) {
        "en" -> LANGUAGE_ENGLISH
        "ja" -> LANGUAGE_JAPANESE
        "zh" -> LANGUAGE_CHINESE
        "ko" -> LANGUAGE_KOREAN
        "all" -> LANGUAGE_MULTI
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    override val supportSpeechless = true

    override val searchPopularPath = ""

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            val frag = request.url.fragment.orEmpty()
            var response = chain.proceed(request)

            if ((!response.isSuccessful && !response.peekBody(64).string().contains("<html", true)) ||
                !frag.startsWith("fallback")
            ) {
                return@addInterceptor response
            }

            frag.substringAfter("fallback").parseAs<List<String>>().forEach {
                response.close()
                response = chain.proceed(request.newBuilder().url(it).build())
                if (response.isSuccessful) return@addInterceptor response
            }
            response
        }
    }

    private val languages: List<Pair<String, String>> = listOf(
        Pair(LANGUAGE_ENGLISH, "19"),
        Pair(LANGUAGE_JAPANESE, "2"),
        Pair(LANGUAGE_CHINESE, "10197"),
        Pair(LANGUAGE_KOREAN, "84916"),
    )
    private val langCode = languages.firstOrNull { lang -> lang.first == mangaLang }?.second

    // popular

    override val popularMangaUrl get() = basicSearchUrl(
        0,
        "",
        FilterList(
            SortOrderFilter(emptyList())
                .apply {
                    state = 2
                },
        ),
    )

    override val latestUpdatesUrl get() = basicSearchUrl(
        0,
        "",
        FilterList(
            SortOrderFilter(emptyList())
                .apply {
                    state = 1
                },
        ),
    )

    override fun Element.mangaLang(): String {
        val tags = attr("data-tags").orEmpty().split(" ")

        return when {
            langCode in tags -> mangaLang
            "10314" in tags -> LANGUAGE_SPEECHLESS
            else -> "other"
        }
    }

    override fun popularMangaSelector() = ".gallery a:not([rel~=sponsored])"
    override fun popularMangaNextPageSelector() = "a.next"

    override fun Element.mangaUrl() = absUrl("href")

    override fun Element.getCover() = selectFirst(".cover img")?.withFallback()

    override fun Element.mangaThumbnail() = selectFirst("img")?.withFallback()

    private fun Element.withFallback(): String? {
        val image = imgAttr().ifEmpty { return null }
        val fallbacks = attr("data-fallbacks")
        return if (fallbacks.isBlank()) image else "$image#fallback$fallbacks"
    }

    // Details
    override val mangaDetailInfoSelector = "#info-block"
    override val idPrefixUri = "g"

    // Tags
    override fun getInfoSelector(tag: String) = ".field-name:contains($tag) .tags a"
    override fun Element.infoTagName() = selectFirst(".name")?.ownText() ?: ""

    // Pages
    override val serverPrefix = "i"

    override fun Element.getInfoPages(document: Document?) = document?.getInfo("Pages")?.let { "**Pages**: $it" }

    override fun Element.getTime() = selectFirst(".tags time")?.attr("datetime").toDate(null)

    override val thumbnailSelector = "#thumbnail-container .thumb-container"

    // search
    override fun basicSearchUrl(page: Int, query: String, filters: FilterList): String {
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()

        return "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.ifEmpty { "*" })
            sortOrderFilter?.state?.let { addQueryParameter("sort", getSortOrderURIs()[it].second) }
            addQueryParameter("lang", mangaLang)
        }.build().toString()
    }

    // Filters
    override val supportsFilterFetching = false

    override fun getSortOrderURIs() = listOf(
        "Relevance" to "relevance",
        "Newest" to "newest",
        "Most favorited" to "most-favorited",
        "Most Liked" to "most-liked",
    )
}
