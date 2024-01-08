package eu.kanade.tachiyomi.extension.all.simplycosplay

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class SimplyCosplay : HttpSource(), ConfigurableSource {

    override val name = "Simply Cosplay"

    override val lang = "all"

    override val baseUrl = "https://www.simply-cosplay.com"

    private val apiUrl = "https://api.simply-porn.com/v2".toHttpUrl()

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::tokenIntercept)
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    private val json: Json by injectLazy()

    private val preference by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun tokenIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host != apiUrl.host) {
            return chain.proceed(request)
        }

        val url = request.url.newBuilder()
            .setQueryParameter("token", preference.getToken())
            .build()

        val response = chain.proceed(
            request.newBuilder()
                .url(url)
                .build(),
        )

        if (response.isSuccessful.not() && response.code == 403) {
            response.close()

            val newToken = fetchNewToken()

            preference.putToken(newToken)

            val newUrl = request.url.newBuilder()
                .setQueryParameter("token", newToken)
                .build()

            return chain.proceed(
                request.newBuilder()
                    .url(newUrl)
                    .build(),
            )
        }

        return response
    }

    private fun fetchNewToken(): String {
        val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()

        val scriptUrl = document.selectFirst("script[src*=main]")
            ?.attr("abs:src")
            ?: throw IOException(TOKEN_EXCEPTION)

        val scriptContent = client.newCall(GET(scriptUrl, headers)).execute()
            .use { it.body.string() }
            .replace("\'", "\"")

        return TokenRegex.find(scriptContent)?.groupValues?.get(1)
            ?: throw IOException(TOKEN_EXCEPTION)
    }

    private fun browseUrlBuilder(endPoint: String, sort: String, page: Int): HttpUrl.Builder {
        return apiUrl.newBuilder().apply {
            addPathSegment(endPoint)
            addQueryParameter("sort", sort)
            addQueryParameter("limit", limit.toString())
            addQueryParameter("page", page.toString())
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = browseUrlBuilder(preference.getDefaultBrowse(), "hot", page)

        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        runCatching { fetchTags() }

        val result = response.parseAs<browseResponse>()

        val entries = result.data.map(BrowseItem::toSManga)
        val hasNextPage = result.data.size >= limit

        return MangasPage(entries, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = browseUrlBuilder(preference.getDefaultBrowse(), "new", page)

        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(SEARCH_PREFIX)) {
            val url = query.substringAfter(SEARCH_PREFIX)
            val manga = SManga.create().apply { this.url = url }
            fetchMangaDetails(manga).map {
                MangasPage(listOf(it), false)
            }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.getSort() ?: "new"

        val url = browseUrlBuilder("search", sort, page).apply {
            if (query.isNotEmpty()) {
                addQueryParameter("query", query)
            }
            filters.map { filter ->
                when (filter) {
                    is TagFilter -> {
                        filter.getSelected().forEachIndexed { index, tag ->
                            addQueryParameter(
                                "filter[tag_names][$index]",
                                tag.name.replace(" ", "+"),
                            )
                        }
                    }
                    is TypeFilter -> {
                        filter.getValue().let {
                            if (it.isNotEmpty()) {
                                addQueryParameter("filter[type][0]", it)
                            }
                        }
                    }
                    else -> { }
                }
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    private var tagList: List<String> = emptyList()
    private var tagsFetchAttempt = 0
    private var tagsFetchFailed = false

    private fun fetchTags() {
        if (tagsFetchAttempt < 3 && (tagList.isEmpty() || tagsFetchFailed)) {
            val tags = runCatching {
                client.newCall(tagsRequest())
                    .execute().use(::tagsParse)
            }

            tagsFetchFailed = tags.isFailure
            tagList = tags.getOrElse {
                Log.e("SimplyHentaiTags", it.stackTraceToString())
                emptyList()
            }
            tagsFetchAttempt++
        }
    }

    private fun tagsRequest(): Request {
        val url = apiUrl.newBuilder()
            .addPathSegment("search")
            .build()

        return GET(url, headers)
    }

    private fun tagsParse(response: Response): List<String> {
        val result = response.parseAs<TagsResponse>()

        return result.aggs.tag_names.buckets.map {
            it.key.trim()
        }
    }

    class Tag(name: String) : Filter.CheckBox(name)

    class TagFilter(title: String, tags: List<String>) :
        Filter.Group<Tag>(title, tags.map(::Tag)) {

        fun getSelected() = state.filter { it.state }
    }

    class TypeFilter(title: String, private val types: List<String>) :
        Filter.Select<String>(title, types.toTypedArray()) {

        fun getValue() = types[state].lowercase()
    }

    class SortFilter(title: String, private val sorts: List<String>) :
        Filter.Select<String>(title, sorts.toTypedArray()) {

        fun getSort() = sorts[state].lowercase()
    }

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter("Sort", listOf("New", "Hot")),
            TypeFilter("Type", listOf("", "Image", "Gallery")),
        )

        if (tagList.isNotEmpty()) {
            filters += TagFilter("Tags", tagList)
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Press 'Reset' to attempt to show tags"),
            )
        }

        return FilterList(filters)
    }

    private fun mangaUrlBuilder(dbUrl: String): HttpUrl.Builder {
        val pathSegments = dbUrl.split("/")
        val type = pathSegments[1]
        val slug = pathSegments[3]

        return apiUrl.newBuilder().apply {
            addPathSegment(type)
            addPathSegments(slug)
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = mangaUrlBuilder(manga.url)

        return GET(url.build(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<detailsResponse>()

        return result.data.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = manga.url.split("/")[1].replaceFirstChar {
                        if (it.isLowerCase()) {
                            it.titlecase(
                                Locale.ROOT,
                            )
                        } else {
                            it.toString()
                        }
                    }
                    date_upload = manga.description?.substringAfterLast("Date: ").parseDate()
                },
            ),
        )
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val url = mangaUrlBuilder(chapter.url)

        return GET(url.build(), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<pageResponse>()

        return result.data.images?.mapIndexedNotNull { index, image ->
            if (image.urls.url.isNullOrEmpty()) {
                null
            } else {
                Page(index, "", image.urls.url)
            }
        }
            ?: Page(1, "", result.data.preview.urls.url).let(::listOf)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = BROWSE_TYPE_PREF_KEY
            title = BROWSE_TYPE_TITLE
            entries = arrayOf("Gallery", "Image")
            entryValues = arrayOf("gallery", "image")
            summary = "%s"
            setDefaultValue("gallery")
        }.also(screen::addPreference)
    }

    private fun SharedPreferences.getDefaultBrowse() =
        getString(BROWSE_TYPE_PREF_KEY, "gallery")!!

    private fun SharedPreferences.getToken() =
        getString(DEFAULT_TOKEN_PREF, DEFAULT_FALLBACK_TOKEN) ?: DEFAULT_FALLBACK_TOKEN

    private fun SharedPreferences.putToken(token: String) =
        edit().putString(DEFAULT_TOKEN_PREF, token).commit()

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun String?.parseDate(): Long {
        return runCatching { dateFormat.parse(this!!)!!.time }
            .getOrDefault(0L)
    }

    companion object {
        private const val limit = 20
        const val SEARCH_PREFIX = "url:"

        private const val DEFAULT_TOKEN_PREF = "default_token_pref"
        private const val DEFAULT_FALLBACK_TOKEN = "01730876"
        private const val TOKEN_EXCEPTION = "Unable to fetch new Token"
        private val TokenRegex = Regex("""token\s*:\s*"([^\"]+)""")

        private val dateFormat by lazy { SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH) }

        private const val BROWSE_TYPE_PREF_KEY = "default_browse_type_key"
        private const val BROWSE_TYPE_TITLE = "Default Browse List"
    }

    override fun chapterListParse(response: Response) =
        throw UnsupportedOperationException("Not implemented")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not implemented")
}
