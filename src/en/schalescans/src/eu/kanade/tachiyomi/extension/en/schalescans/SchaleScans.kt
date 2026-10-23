package eu.kanade.tachiyomi.extension.en.schalescans

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
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class SchaleScans :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override val supportsRelatedMangas = true

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    private val domain get() = baseUrl.toHttpUrl().topPrivateDomain()!!

    private val apiHeaders: Headers get() = headersBuilder().add("apikey", API_KEY).build()

    private val showNsfw get() = preferences.getBoolean(PREF_SHOW_NSFW, true)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Origin")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val request = chain.request()
        val host = request.url.host
        if (host.contains("mangadex.org")) {
            chain.proceed(request.newBuilder().header("Referer", "https://mangadex.org/").build())
        } else {
            chain.proceed(request)
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SiteFilter(showNsfw), RatingFilter(showNsfw)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected

        val url = "$API_URL/series".toHttpUrl().newBuilder().apply {
            addQueryParameter(
                "select",
                buildString {
                    append("id,title,slug,cover_url,is_r18,created_at,chapters(published_at,created_at)")
                    if (genre != null) append(",series_genres!inner(genres!inner(name))")
                },
            )
            addQueryParameter("chapters.pages_ready", "eq.true")
            addQueryParameter("chapters.order", "published_at.desc.nullslast")
            addQueryParameter("chapters.limit", "1")
            if (query.isNotBlank()) addQueryParameter("title", "ilike.*${query.trim()}*")
            genre?.let { addQueryParameter("series_genres.genres.name", "eq.$it") }
            filters.firstInstanceOrNull<SiteFilter>()?.selected?.let { addQueryParameter("is_r18", "eq.$it") }
            filters.firstInstanceOrNull<RatingFilter>()?.selected?.let { addQueryParameter("content_rating", "in.(${it.joinToString(",")})") }
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.let { addQueryParameter("status", "eq.$it") }
            sort?.let { addQueryParameter("order", it) }
        }.build()

        val series = client.get(url, apiHeaders).parseAs<List<SeriesDto>>()
            .filter { it.hasChapters }
            .let { list -> if (sort == null) list.sortedByDescending { it.latestAt } else list }

        return MangasPage(series.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != domain && !url.host.endsWith(".$domain")) return null
        if (url.pathSegments.firstOrNull() !in setOf("manga", "read")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        val apiUrl = "$API_URL/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,title,slug,cover_url,is_r18,created_at,chapters(published_at,created_at)")
            .addQueryParameter("slug", "eq.$slug")
            .addQueryParameter("chapters.limit", "1")
            .build()

        return client.get(apiUrl, apiHeaders).parseAs<List<SeriesDto>>().firstOrNull()?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$API_URL/series".toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,title,slug,cover_url,description,status,is_r18,alt_titles,authors,mangadex_id,anilist_id,mal_id,store_links,read_elsewhere,series_genres(genres(name))," +
                    "chapters(id,chapter_number,volume_number,title,published_at,created_at)",
            )
            .addQueryParameter("id", "eq.${manga.url}")
            .addQueryParameter("chapters.pages_ready", "eq.true")
            .addQueryParameter("chapters.order", "chapter_number.desc")
            .build()

        val series = client.get(url, apiHeaders).parseAs<List<SeriesDetailsDto>>().first()
        return SMangaUpdate(series.toSManga(), series.toSChapterList())
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = "$API_URL/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,title,slug,cover_url,is_r18,content_rating,created_at,series_genres(genres(name)),chapters(published_at,created_at)")
            .addQueryParameter("is_r18", "eq.${manga.memo[MEMO_R18]!!.boolean}")
            .addQueryParameter("chapters.pages_ready", "eq.true")
            .addQueryParameter("chapters.order", "published_at.desc.nullslast")
            .addQueryParameter("chapters.limit", "1")
            .build()

        val series = client.get(url, apiHeaders).parseAs<List<SeriesDto>>()
        val genres = series.first { it.id == manga.url }.genres
        val showNsfw = showNsfw

        return series
            .filter { it.id != manga.url && it.hasChapters && (showNsfw || !it.isAdult) }
            .map { it to it.genres.let { other -> (genres intersect other).size.toFloat() / (genres union other).size } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<SeriesDto, Float>> { it.second }.thenByDescending { it.first.latestAt })
            .map { it.first.toSManga() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$API_URL/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "series(is_r18),chapter_pages(object_key)")
            .addQueryParameter("id", "eq.${chapter.url}")
            .addQueryParameter("chapter_pages.order", "page_number.asc")
            .build()

        val data = client.get(url, apiHeaders).parseAs<List<ChapterPagesDto>>().first()
        val imageHost = if (data.series.isR18) "https://img-r18.$domain" else "https://img.$domain"

        return data.pages.mapIndexed { i, page ->
            Page(i, imageUrl = "$imageHost/${page.objectKey}")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "${siteUrl(manga.memo[MEMO_R18]!!.boolean)}/manga/${manga.memo[MEMO_SLUG]!!.string}"

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo[MEMO_SLUG]!!.string
        val number = chapter.memo[MEMO_NUMBER]!!.string
        return "${siteUrl(chapter.memo[MEMO_R18]!!.boolean)}/read/$slug/$number"
    }

    private fun siteUrl(isR18: Boolean) = if (isR18) "https://r18.$domain" else baseUrl

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$API_URL/genres".toHttpUrl().newBuilder()
            .addQueryParameter("select", "name")
            .addQueryParameter("order", "name.asc")
            .build()

        return client.get(url, apiHeaders).parseAs<List<GenreDto>>()
            .map { it.name }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SiteFilter(showNsfw))
            add(RatingFilter(showNsfw))
            add(StatusFilter())
            data?.parseAs<List<String>>()?.let { add(GenreFilter(it)) }
            add(SortFilter())
        },
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_NSFW
            title = "Show 18+ content"
            summary = "Include R18 site series and erotica/pornographic rated series by default. Can still be changed per search with the Site and Content rating filters."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val API_URL = "https://xqjjktixlttbqbsdpaxe.supabase.co/rest/v1"
        private const val API_KEY = "sb_publishable__ow-g4H00jio3RK5qQaJuA_87iakWRc"
        private const val PREF_SHOW_NSFW = "pref_show_nsfw"
    }
}
