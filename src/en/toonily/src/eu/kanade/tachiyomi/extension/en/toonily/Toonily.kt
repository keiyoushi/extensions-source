package eu.kanade.tachiyomi.extension.en.toonily

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.parser.Parser
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Toonily :
    MadaraNoAjax(),
    ConfigurableSource {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yy", Locale.US)

    private val preferences by getPreferencesLazy()

    // the site filters mature titles server side based on this cookie, admin-ajax ignores it
    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie { listOf("toonily-mature" to if (showMature()) "1" else "0") }
        addInterceptor(::hdCoverInterceptor)
    }

    override val mangaSubString = "serie"
    override val genreDirectory get() = "genre"
    override val sendViewCount = false
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorDescription = "div.content-area div.summary__content"

    override fun getHomeUrl() = "$baseUrl/$mangaSubString/?m_orderby=views"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value.orEmpty()
        filters.firstInstanceOrNull<TagFilter>()?.slug?.takeIf(String::isNotEmpty)?.let { tag ->
            return archivePage(page, sort, "/tag/$tag/")
        }

        // the site redirects `?s=` to `/search/<slug>` and drops the page number on the way
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            query.lowercase().replace(titleSpecialCharactersRegex, "-").trim('-')
                .takeIf(String::isNotEmpty)?.let(::addPathSegment)
            if (page > 1) addPathSegments("page/$page")
            addPathSegment("")
            filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = super.fetchMangaUpdate(
        manga.apply {
            url = url.replace("/webtoon/", "/$mangaSubString/")
        },
        chapters,
        fetchDetails,
        fetchChapters,
    )

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async { super.fetchFilterData().parseAs<List<GenreRoute>>() }
        val tags = async { fetchTags() }
        FilterData(genres.await(), tags.await()).toJsonElement()
    }

    private suspend fun fetchTags(): List<String> = client.get("$baseUrl/wp-manga-tag-sitemap.xml", Fetch.DOCUMENT)
        .asJsoup(Parser.xmlParser())
        .select("loc")
        .mapNotNull { loc ->
            loc.text().toHttpUrlOrNull()?.pathSegments
                ?.takeIf { it.firstOrNull() == "tag" }
                ?.getOrNull(1)
                ?.takeIf(String::isNotEmpty)
        }
        .distinct()
        .sorted()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        val genres = filterData?.genres.orEmpty()
        val tags = filterData?.tags.orEmpty()
        return FilterList(
            buildList {
                add(TextFilter(intl["author_filter_title"], "author"))
                add(TextFilter(intl["artist_filter_title"], "artist"))
                add(TextFilter(intl["year_filter_title"], "release"))
                add(StatusFilter(intl["status_filter_title"], statusFilterOptions))
                add(SortFilter(intl["order_by_filter_title"], orderByFilterOptions))
                add(AdultFilter(intl["adult_content_filter_title"], adultFilterOptions))
                if (genres.isNotEmpty()) {
                    add(Filter.Separator())
                    add(GenreConditionFilter(intl["genre_condition_filter_title"], genreConditionFilterOptions))
                    add(GenreFilter(intl["genre_filter_title"], genres))
                }
                if (tags.isNotEmpty()) {
                    add(Filter.Separator())
                    add(Filter.Header("Tag ignores the search text and other filters, except Order By"))
                    add(TagFilter(tags))
                }
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_MATURE_PREF
            title = "Show mature content"
            summary = "Show adult titles when browsing and searching"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun showMature() = preferences.getBoolean(SHOW_MATURE_PREF, false)

    private fun hdCoverInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        return if (
            url.host.startsWith("static") && // covers are hosted on the static cdn, panels on data cdn
            url.pathSegments.lastOrNull()?.contains(sdCoverRegex) == true
        ) {
            try {
                val newUrl = url.newBuilder()
                    .removePathSegment(url.pathSegments.lastIndex)
                    .addPathSegment(
                        sdCoverRegex.replace(
                            url.pathSegments.last(),
                            "$1",
                        ),
                    ).build()
                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(newRequest)
                    .also { assert(it.isSuccessful) }
            } catch (_: Throwable) {
                chain.proceed(request)
            }
        } else {
            chain.proceed(request)
        }
    }

    companion object {
        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        val sdCoverRegex = Regex("""-[0-9]+x[0-9]+(\.\w+)$""")
        private const val SHOW_MATURE_PREF = "pref_show_mature"
    }
}
