package eu.kanade.tachiyomi.extension.en.aethonwebcomics

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class AethonWebcomics :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = api("webcomic_episode")
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", EPISODE_SERIES_FIELDS)
            .build()
        val seriesIds = client.get(url, Fetch.XHR).parseAs<List<EpisodeSeries>>().map { it.meta.seriesId }.distinct()

        return fetchSeries(seriesUrl().addQueryParameter("include", seriesIds.joinToString(",")).sorted("include", "asc"), page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()
        val url = seriesUrl().sorted(sort.orderBy, sort.order).apply {
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            filters.firstInstanceOrNull<GenreFilter>()?.id?.let { addQueryParameter("webcomic_genre", it.toString()) }
        }
        return fetchSeries(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val seriesQuery = when (url.pathSegments[0]) {
            "webcomics" -> seriesUrl().addQueryParameter("slug", slug)

            "comic-episode" -> {
                val episodeUrl = api("webcomic_episode")
                    .addQueryParameter("slug", slug)
                    .addQueryParameter("_fields", EPISODE_SERIES_FIELDS)
                    .build()
                val episode = client.get(episodeUrl, Fetch.XHR).parseAs<List<EpisodeSeries>>().firstOrNull() ?: return null
                seriesUrl().addQueryParameter("include", episode.meta.seriesId.toString())
            }

            else -> return null
        }
        return fetchSeries(seriesQuery, 1).mangas.firstOrNull()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/webcomics/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                fetchSeries(seriesUrl().addQueryParameter("slug", manga.url), 1).mangas.first()
            } else {
                manga
            }
        }
        val chapterList = async { if (fetchChapters) getChapters(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comic-episode/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".mf-member-lock") != null) {
            throw Exception("Premium episode: log in via WebView with an account that has Premium")
        }
        return document.select("[data-webcomic-reader] img[data-panel-img]").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("src"))
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val url = api("webcomic_genre")
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", "id,name")
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<Genre>>().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>() ?: return FilterList(SortFilter())
        return FilterList(GenreFilter(genres), SortFilter())
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_PREMIUM
            title = "Show Premium episodes"
            summary = "List Premium episodes, marked 🔒. They open after logging in via WebView with an account that has Premium."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun api(route: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("wp-json/wp/v2")
        .addPathSegment(route)

    private fun seriesUrl(): HttpUrl.Builder = api("webcomic_series")
        .addQueryParameter("per_page", "24")
        .addQueryParameter("_embed", "wp:term")
        .addQueryParameter("_fields", SERIES_FIELDS)

    private fun HttpUrl.Builder.sorted(orderBy: String, order: String) = addQueryParameter("orderby", orderBy)
        .addQueryParameter("order", order)

    private suspend fun fetchSeries(url: HttpUrl.Builder, page: Int): MangasPage {
        val response = client.get(url.addQueryParameter("page", page.toString()).build(), Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val series = response.parseAs<List<Series>>()
        val covers = fetchCovers(series.map { it.meta.cardImageId })

        return MangasPage(series.map { it.toSManga(covers[it.meta.cardImageId]) }, page < totalPages)
    }

    private suspend fun fetchCovers(ids: List<Int>): Map<Int, String> {
        if (ids.isEmpty()) return emptyMap()
        val url = api("media")
            .addQueryParameter("include", ids.joinToString(","))
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", "id,source_url")
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<Media>>().associate { it.id to it.sourceUrl }
    }

    // The series page has the Free and Premium labels, REST has the dates and no filter by series
    private suspend fun getChapters(manga: SManga): List<SChapter> = coroutineScope {
        val showPremium = preferences.getBoolean(PREF_SHOW_PREMIUM, false)
        val cards = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
            .select("[data-webcomic-episode-page] .modfarm-webcomic-episode-card")
            .filter { showPremium || !it.isPremium() }
        val dates = cards.map { it.episodeId() }.chunked(100)
            .map { async { fetchDates(it) } }
            .awaitAll()
            .flatten()
            .associate { it.id to it.date }

        cards.map { card ->
            val link = card.selectFirst("a")!!
            val name = card.selectFirst(".modfarm-webcomic-episode-card-title")!!.text()
            SChapter.create().apply {
                url = link.absUrl("href").toHttpUrl().pathSegments[1]
                this.name = if (card.isPremium()) "🔒 $name" else name
                chapter_number = card.selectFirst(".modfarm-webcomic-episode-number")?.text()?.removePrefix("#")?.toFloatOrNull() ?: -1f
                date_upload = dates[card.episodeId()] ?: 0L
            }
        }
    }

    private suspend fun fetchDates(ids: List<Int>): List<EpisodeDate> {
        val url = api("webcomic_episode")
            .addQueryParameter("include", ids.joinToString(","))
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", "id,date_gmt")
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    private fun Element.isPremium() = selectFirst(".modfarm-webcomic-episode-card-meta:contains(Premium)") != null

    private fun Element.episodeId() = id().removePrefix("webcomic-episode-").toInt()

    private companion object {
        const val PREF_SHOW_PREMIUM = "show_premium_episodes"
        const val EPISODE_SERIES_FIELDS = "meta.webcomic_series_id"
        const val SERIES_FIELDS = "slug,title,_links,_embedded,meta.webcomic_status,meta.webcomic_series_card_image_id," +
            "meta.webcomic_series_description,meta.webcomic_logline,meta.webcomic_age_rating," +
            "meta.webcomic_update_cadence,meta.webcomic_update_day"
    }
}
