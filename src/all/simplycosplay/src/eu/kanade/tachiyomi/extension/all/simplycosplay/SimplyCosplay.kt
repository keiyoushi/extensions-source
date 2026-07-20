package eu.kanade.tachiyomi.extension.all.simplycosplay

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class SimplyCosplay :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl = "https://api.simply-porn.com/v2".toHttpUrl()

    private val preference by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(::tokenIntercept)
        rateLimit(2)
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

    private fun browseUrlBuilder(endPoint: String, sort: String, page: Int): HttpUrl.Builder = apiUrl.newBuilder().apply {
        addPathSegment(endPoint)
        addQueryParameter("sort", sort)
        addQueryParameter("limit", LIMIT.toString())
        addQueryParameter("page", page.toString())
    }

    private fun browseResponse.toMangasPage(): MangasPage {
        val entries = data.map(BrowseItem::toSManga)
        val hasNextPage = data.size >= LIMIT

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = browseUrlBuilder(preference.getDefaultBrowse(), "hot", page)

        return client.get(url.build()).parseAs<browseResponse>().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = browseUrlBuilder(preference.getDefaultBrowse(), "new", page)

        return client.get(url.build()).parseAs<browseResponse>().toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host !in listOf("simply-cosplay.com", "www.simply-cosplay.com")) {
            return null
        }
        if (url.pathSegments.size < 3) {
            return null
        }

        val mangaUrl = "/${url.pathSegments[0]}/new/${url.pathSegments[2]}"

        return fetchMangaDetails(mangaUrl)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.getSort() ?: "new"

        val url = browseUrlBuilder("search", sort, page).apply {
            if (query.isNotEmpty()) {
                addQueryParameter("query", query)
            }
            filters.forEach { filter ->
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

        return client.get(url.build()).parseAs<browseResponse>().toMangasPage()
    }

    class Tag(name: String) : Filter.CheckBox(name)

    class TagFilter(title: String, tags: List<String>) : Filter.Group<Tag>(title, tags.map(::Tag)) {

        fun getSelected() = state.filter { it.state }
    }

    class TypeFilter(title: String, private val types: List<String>) : Filter.Select<String>(title, types.toTypedArray()) {

        fun getValue() = types[state].lowercase()
    }

    class SortFilter(title: String, private val sorts: List<String>) : Filter.Select<String>(title, sorts.toTypedArray()) {

        fun getSort() = sorts[state].lowercase()
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.newBuilder().addPathSegment("search").build()
        val result = client.get(url).parseAs<TagsResponse>()

        return result.aggs.tag_names.buckets.map { it.key.trim() }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter("Sort", listOf("New", "Hot")),
            TypeFilter("Type", listOf("", "Image", "Gallery")),
        )

        val tagList = data?.let { runCatching { it.parseAs<List<String>>() }.getOrNull() }

        if (!tagList.isNullOrEmpty()) {
            filters += TagFilter("Tags", tagList)
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

    private suspend fun fetchMangaDetails(mangaUrl: String): SManga {
        val url = mangaUrlBuilder(mangaUrl)

        return client.get(url.build()).parseAs<detailsResponse>().data.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) fetchMangaDetails(manga.url) else manga

        val updatedChapters = if (fetchChapters) {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = manga.url.split("/")[1].replaceFirstChar {
                        if (it.isLowerCase()) {
                            it.titlecase(Locale.ROOT)
                        } else {
                            it.toString()
                        }
                    }
                    date_upload = updatedManga.description?.substringAfterLast("Date: ").parseDate()
                },
            )
        } else {
            chapters
        }

        return SMangaUpdate(manga = updatedManga, chapters = updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = mangaUrlBuilder(chapter.url)
        val result = client.get(url.build()).parseAs<pageResponse>()

        return result.data.images?.mapIndexedNotNull { index, image ->
            image.urls.url?.takeIf { it.isNotEmpty() }?.let { Page(index, imageUrl = it) }
        }
            ?: listOf(Page(0, imageUrl = result.data.preview.urls.url))
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

    private fun SharedPreferences.getDefaultBrowse() = getString(BROWSE_TYPE_PREF_KEY, "gallery")!!

    private fun SharedPreferences.getToken() = getString(DEFAULT_TOKEN_PREF, DEFAULT_FALLBACK_TOKEN) ?: DEFAULT_FALLBACK_TOKEN

    private fun SharedPreferences.putToken(token: String) = edit().putString(DEFAULT_TOKEN_PREF, token).commit()

    private fun String?.parseDate(): Long = runCatching { dateFormat.parse(this!!)!!.time }
        .getOrDefault(0L)

    companion object {
        private const val LIMIT = 20

        private const val DEFAULT_TOKEN_PREF = "default_token_pref"
        private const val DEFAULT_FALLBACK_TOKEN = "01730876"
        private const val TOKEN_EXCEPTION = "Unable to fetch new Token"
        private val TokenRegex = Regex("""token\s*:\s*"([^"]+)""")

        private val dateFormat by lazy { SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH) }

        private const val BROWSE_TYPE_PREF_KEY = "default_browse_type_key"
        private const val BROWSE_TYPE_TITLE = "Default Browse List"
    }
}
