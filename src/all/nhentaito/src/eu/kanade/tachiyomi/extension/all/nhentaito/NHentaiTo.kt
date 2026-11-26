package eu.kanade.tachiyomi.extension.all.nhentaito

import eu.kanade.tachiyomi.multisrc.galleryadults.FavoriteFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.RandomEntryFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SortOrderFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.SpeechlessFilter
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.toDate
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
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

            if (!frag.startsWith("fallback") || response.isRealSuccess()) {
                return@addInterceptor response
            }

            frag.substringAfter("fallback").parseAs<List<String>>().forEach {
                response.close()
                response = chain.proceed(request.newBuilder().url(it).build())
                if (response.isRealSuccess()) return@addInterceptor response
            }
            response
        }
    }

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

    override fun popularMangaSelector() = ".gallery a:not([rel~=sponsored])"
    override fun popularMangaNextPageSelector() = "a.next"

    override fun Element.mangaUrl() = absUrl("href")

    override fun Element.getCover() = selectFirst("#cover img")?.withFallback()

    override fun Element.mangaThumbnail() = selectFirst("img")?.withFallback()

    private fun Element.withFallback(): String? {
        val image = imgAttr().ifEmpty { return null }
        val fallbacks = attr("data-fallbacks")
        return if (fallbacks.isBlank()) image else "$image#fallback$fallbacks"
    }

    // Details
    override val mangaDetailInfoSelector = "#bigcontainer"
    override val idPrefixUri = "g"

    // Tags
    override fun getInfoSelector(tag: String) = "#info-block .field-name:contains($tag) .tags a"
    override fun Element.infoTagName() = selectFirst(".name")?.ownText() ?: ""

    // Pages
    override val serverPrefix = "i"

    override fun Element.totalPages() = getInfo("Pages")

    override fun Element.getTime() = selectFirst(".tags time")?.attr("datetime").toDate(null)

    override val thumbnailSelector = "#thumbnail-container .thumb-container"

    // search
    override fun basicSearchUrl(page: Int, query: String, filters: FilterList): String {
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()
        val excludeTags = filters.firstInstanceOrNull<ExcludeFilter>()
        val pageCountFilter = filters.firstInstanceOrNull<PageCountFilter>()

        return "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.ifEmpty { "*" })
            sortOrderFilter?.state?.let { addQueryParameter("sort", getSortOrderURIs()[it].second) }
            excludeTags?.state?.let { addQueryParameter("exclude", it) }
            pageCountFilter?.let { filter ->
                filter.min?.let { addQueryParameter("min_pages", it.toString()) }
                filter.max?.let { addQueryParameter("max_pages", it.toString()) }
            }
            addQueryParameter("lang", mangaLang)
        }.build().toString()
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val isSpecialPath = query.toIntOrNull() != null ||
            filters.firstInstanceOrNull<RandomEntryFilter>()?.state == true ||
            filters.firstInstanceOrNull<FavoriteFilter>()?.state == true ||
            (supportSpeechless && filters.firstInstanceOrNull<SpeechlessFilter>()?.state == true)

        if (isSpecialPath) return super.getSearchMangaList(page, query, filters)

        // nhentai.to's /search endpoint natively supports sort/exclude/min_pages/max_pages.
        // The theme routes empty-query searches to popularMangaUrl/latestUpdatesUrl,
        // which are overridden here with a fresh FilterList and would drop the user's
        // filters, so build the basic search URL directly with the active state.
        return parseSearchManga(client.get(basicSearchUrl(page, query, filters).addPageUri(page), Fetch.DOCUMENT))
    }

    // Filters
    override val supportsFilterFetching = false

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            ExcludeFilter(),
            PageCountFilter(),
        ) + super.getFilterList(data).list,
    )

    override fun getSortOrderURIs() = listOf(
        "Relevance" to "relevance",
        "Newest" to "newest",
        "Most favorited" to "most-favorited",
        "Most Liked" to "most-liked",
    )

    class ExcludeFilter : Filter.Text("Exclude Tags (seperate by comma)")

    class PageCountText(name: String) : Filter.Text(name)

    class PageCountFilter : Filter.Group<PageCountText>("Pages", listOf(PageCountText("Min"), PageCountText("Max"))) {
        val min get() = state[0].state.trim().toIntOrNull()
        val max get() = state[1].state.trim().toIntOrNull()
    }

    fun Response.isRealSuccess() = isSuccessful && !peekBody(64).string().contains("<html", true)
}
