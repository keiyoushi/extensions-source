package eu.kanade.tachiyomi.extension.all.comicklive

import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.internal.closeQuietly
import okio.IOException
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class Comick(
    override val lang: String,
    private val siteLang: String = lang,
) : HttpSource(), ConfigurableSource {

    override val name = "Comick (Unoriginal)"

    override val supportsLatest = true

    private val preferences = getPreferences()

    override val baseUrl: String
        get() {
            val index = preferences.getString(DOMAIN_PREF, "0")!!.toInt()
                .coerceAtMost(domains.size - 1)

            return domains[index]
        }

    override val client = network.cloudflareClient.newBuilder()
        // Referer in interceptor due to domain change preference
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Referer", "$baseUrl/")
                .build()

            chain.proceed(request)
        }
        // fix disk cache
        .apply {
            val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
            if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/comics/top".toHttpUrl().newBuilder().apply {
            val days = when (page) {
                1, 4 -> 7
                2, 5 -> 30
                3, 6 -> 90
                else -> throw UnsupportedOperationException()
            }
            val type = when (page) {
                1, 2, 3 -> "follow"
                4, 5, 6 -> "most_follow_new"
                else -> throw UnsupportedOperationException()
            }
            addQueryParameter("days", days.toString())
            addQueryParameter("type", type)
            fragment(page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Data<List<BrowseComic>>>()
        val page = response.request.url.fragment!!.toInt()

        return MangasPage(
            mangas = data.data.map(BrowseComic::toSManga),
            hasNextPage = page < 6,
        )
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/api/chapters/latest?order=new&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<Data<List<BrowseComic>>>()

        return MangasPage(
            mangas = data.data.map(BrowseComic::toSManga),
            hasNextPage = data.data.size == 100,
        )
    }

    private var nextCursor: String? = null

    private val spaceSlashRegex = Regex("[ /]")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) {
            nextCursor = null
        }

        val url = "$baseUrl/api/search".toHttpUrl().newBuilder().apply {
            filters.firstInstance<SortFilter>().let {
                addQueryParameter("order_by", it.selected)
                addQueryParameter("order_direction", if (it.state!!.ascending) "asc" else "desc")
            }
            filters.firstInstanceOrNull<GenreFilter>()?.let { genre ->
                genre.included.forEach {
                    addQueryParameter("genres", it)
                }
                genre.excluded.forEach {
                    addQueryParameter("excludes", it)
                }
            }
            filters.firstInstanceOrNull<TagFilterText>()?.let { text ->
                text.state.split(",").filter(String::isNotBlank).forEach {
                    val value = it.trim().lowercase().replace(spaceSlashRegex, "-")
                    addQueryParameter(
                        if (value.startsWith("-")) "excluded_tags" else "tags",
                        value.replaceFirst("-", ""),
                    )
                }
            }
            filters.firstInstanceOrNull<TagFilter>()?.let { tag ->
                tag.included.forEach {
                    addQueryParameter("tags", it)
                }
                tag.excluded.forEach {
                    addQueryParameter("excluded_tags", it)
                }
            }
            filters.firstInstance<DemographicFilter>().checked.forEach {
                addQueryParameter("demographic", it)
            }
            filters.firstInstance<CreatedAtFilter>().selected?.let {
                addQueryParameter("time", it)
            }
            filters.firstInstance<TypeFilter>().checked.forEach {
                addQueryParameter("country", it)
            }
            filters.firstInstance<MinimumChaptersFilter>().state.let {
                if (it.isNotBlank()) {
                    if (it.toIntOrNull() == null) {
                        throw Exception("Invalid minimum chapters value: $it")
                    }
                    addQueryParameter("minimum", it)
                }
            }
            filters.firstInstance<StatusFilter>().selected?.let {
                addQueryParameter("status", it)
            }
            filters.firstInstance<ReleaseFrom>().selected?.let {
                addQueryParameter("from", it)
            }
            filters.firstInstance<ReleaseTo>().selected?.let {
                addQueryParameter("to", it)
            }
            filters.firstInstance<ContentRatingFilter>().selected?.let {
                addQueryParameter("content_rating", it)
            }
            addQueryParameter("showAll", "false")
            addQueryParameter("exclude_mylist", "false")
            if (query.isNotBlank()) {
                if (query.trim().length < 3) {
                    throw Exception("Query must be at least 3 characters")
                }
                addQueryParameter("q", query.trim())
            }
            addQueryParameter("type", "comic")
            if (page > 1) {
                addQueryParameter("cursor", nextCursor)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>()

        nextCursor = data.cursor

        return MangasPage(
            mangas = data.data.map(BrowseComic::toSManga),
            hasNextPage = data.cursor != null,
        )
    }

    private val metadataClient = client.newBuilder()
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "max-age=${24 * 60 * 60}")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }.build()

    override fun getFilterList(): FilterList = runBlocking(Dispatchers.IO) {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
            DemographicFilter(),
            TypeFilter(),
            CreatedAtFilter(),
            MinimumChaptersFilter(),
            StatusFilter(),
            ContentRatingFilter(),
            ReleaseFrom(),
            ReleaseTo(),
        )

        val response = metadataClient.newCall(
            GET("$baseUrl/api/metadata", headers, CacheControl.FORCE_CACHE),
        ).await()

        val getTags = preferences.getBoolean(GET_TAGS, true)

        val textTags: List<Filter<*>> = listOf(
            Filter.Separator(),
            Filter.Header("Separate tags with commas (,)"),
            Filter.Header("Prepend with dash (-) to exclude"),
            TagFilterText(),
            Filter.Separator(),
        )

        if (!response.isSuccessful) {
            metadataClient.newCall(
                GET("$baseUrl/api/metadata", headers, CacheControl.FORCE_NETWORK),
            ).enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.closeQuietly()
                    }
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Unable to fetch filters", e)
                    }
                },
            )

            if (!getTags) {
                filters.addAll(
                    index = 2,
                    textTags,
                )
            }
            filters.addAll(
                index = 0,
                listOf(
                    Filter.Header("Press 'reset' to load genres ${if (getTags) "and tags" else ""}"),
                    Filter.Separator(),
                ),
            )
            return@runBlocking FilterList(filters)
        }

        val data = try {
            response.parseAs<Metadata>()
        } catch (e: Throwable) {
            Log.e(name, "Unable to parse filters", e)

            if (!getTags) {
                filters.addAll(
                    index = 2,
                    textTags,
                )
            }
            filters.addAll(
                index = 0,
                listOf(
                    Filter.Header("Failed to parse genres ${if (getTags) "and tags" else ""}"),
                    Filter.Separator(),
                ),
            )
            return@runBlocking FilterList(filters)
        }

        filters.add(
            index = 3,
            GenreFilter(data.genres),
        )
        if (!getTags) {
            filters.addAll(
                index = 4,
                textTags,
            )
        } else {
            filters.add(
                index = 4,
                TagFilter(data.tags),
            )
        }
        return@runBlocking FilterList(filters)
    }

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/comic/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.asJsoup()
            .selectFirst("#comic-data")!!.data()
            .parseAs<ComicData>()

        return SManga.create().apply {
            title = data.title
            url = data.slug
            thumbnail_url = data.thumbnail
            status = when (data.status) {
                1 -> SManga.ONGOING
                2 -> if (data.translationCompleted) SManga.COMPLETED else SManga.PUBLISHING_FINISHED
                3 -> SManga.CANCELLED
                4 -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            author = data.authors.joinToString { it.name }
            artist = data.artists.joinToString { it.name }
            description = buildString {
                append(
                    Jsoup.parseBodyFragment(data.desc).wholeText(),
                )

                if (data.titles.isNotEmpty()) {
                    append("\n\n Alternative Titles: \n")
                    data.titles.forEach {
                        append("- ", it.title.trim(), "\n")
                    }
                }
            }.trim()
            genre = buildList {
                when (data.country) {
                    "jp" -> add("Manga")
                    "cn" -> add("Manhua")
                    "ko" -> add("Manhwa")
                }
                when (data.contentRating) {
                    "suggestive" -> add("Content Rating: Suggestive")
                    "erotica" -> add("Content Rating: Erotica")
                }
                addAll(data.genres.map { it.genres.name })
            }.joinToString()
        }
    }

    override fun chapterListRequest(manga: SManga) =
        GET("$baseUrl/api/comics/${manga.url}/chapter-list?lang=$siteLang", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        var data = response.parseAs<ChapterList>()
        var page = 2
        val chapters = data.data.toMutableList()

        while (data.hasNextPage()) {
            val url = response.request.url.newBuilder()
                .addQueryParameter("page", page.toString())
                .build()

            data = client.newCall(GET(url, headers)).execute()
                .parseAs()
            chapters += data.data
            page++
        }

        val mangaSlug = response.request.url.pathSegments[2]

        return chapters.map {
            SChapter.create().apply {
                url = "/comic/$mangaSlug/${it.hid}-chapter-${it.chap}-${it.lang}"
                name = buildString {
                    if (!it.vol.isNullOrBlank()) {
                        append("Vol. ", it.vol, " ")
                    }
                    append("Ch. ", it.chap)
                    if (!it.title.isNullOrBlank()) {
                        append(": ", it.title)
                    }
                }
                date_upload = dateFormat.tryParse(it.createdAt)
                scanlator = it.groups.joinToString()
            }
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.asJsoup()
            .selectFirst("#sv-data")!!.data()
            .parseAs<PageListData>()

        return data.chapter.images.mapIndexed { index, image ->
            Page(index, imageUrl = image.url)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = "Preferred Domain"
            entries = domains
            entryValues = Array(domains.size) { it.toString() }
            summary = "%s"
            setDefaultValue("0")
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = GET_TAGS
            title = "Tags Input Type"
            summaryOn = "Tags will be in a form of scrollable list"
            summaryOff = "Tags will need to be inputted manually"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }
}

private val domains = arrayOf("https://comick.live", "https://comick.art")
private const val DOMAIN_PREF = "domain_pref"
private const val GET_TAGS = "get_tags"
