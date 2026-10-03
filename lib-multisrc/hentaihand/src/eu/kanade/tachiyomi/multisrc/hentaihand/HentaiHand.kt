package eu.kanade.tachiyomi.multisrc.hentaihand

import android.content.SharedPreferences
import android.text.InputType
import android.util.LruCache
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

abstract class HentaiHand :
    KeiSource(),
    ConfigurableSource {

    abstract val chapters: Boolean

    protected open val hhLangId: List<Int> = emptyList()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authIntercept)

    // Popular

    private fun parseMangasPage(response: Response): MangasPage {
        val resp = response.parseAs<ResponseDto<List<MangaDto>>>()
        val hasNextPage = !resp.next_page_url.isNullOrEmpty()
        return MangasPage(resp.data.map { it.toSManga() }, hasNextPage)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popularity")
            .addQueryParameter("order", "desc")
            .addQueryParameter("duration", "all")
        hhLangId.forEachIndexed { index, it ->
            url.addQueryParameter("languages[${-index - 1}]", it.toString())
        }
        // if (altLangId != null) url.addQueryParameter("languages", altLangId.toString())
        return parseMangasPage(client.get(url.build()))
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "uploaded_at")
            .addQueryParameter("order", "desc")
            .addQueryParameter("duration", "all")
        hhLangId.forEachIndexed { index, it ->
            url.addQueryParameter("languages[${-index - 1}]", it.toString())
        }
        return parseMangasPage(client.get(url.build()))
    }

    // Search

    // filter query needs to be resolved to an ID
    // Returns the exact match when present, else the first match,
    // or null if there are no results
    private val filterIdCache = LruCache<String, Int>(100)
    private val queryIdCache = LruCache<String, Pair<String, Int>>(20)

    private suspend fun lookupFilterId(query: String, uri: String, exactMatchOnly: Boolean = false): Int? {
        val key = "$uri:$query"
        if (!exactMatchOnly) {
            filterIdCache.get(key)?.let { return it }
        }
        val lookupUrl = "$baseUrl/api/$uri".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val results = client.get(lookupUrl).parseAs<ResponseDto<List<IdDto>>>().data
        if (results.isEmpty()) {
            return null
        }
        val exact = results.firstOrNull { it.name.equals(query, ignoreCase = true) }?.id
        if (exactMatchOnly) {
            return exact
        }
        return (exact ?: results.first().id).also { filterIdCache.put(key, it) }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        val hasLookupState = filters.any { it is LookupFilter && it.state.isNotBlank() }

        // A plain text `q` search without any id filter returns HTTP 500 on some sites,
        // which breaks tapping a genre tag (the app searches for the tag name as text).
        // Resolve the query to a tag/artist/character id when possible and search by id instead.
        val trimmedQuery = query.trim()
        val cacheKey = trimmedQuery.lowercase()
        val queryFilter: Pair<String, Int>? = if (trimmedQuery.isNotEmpty() && !hasLookupState) {
            queryIdCache.get(cacheKey) ?: run {
                var resolved: Pair<String, Int>? = null
                for (uri in QUERY_LOOKUP_URIS) {
                    val id = try {
                        lookupFilterId(trimmedQuery, uri, exactMatchOnly = true)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    resolved = uri to id
                    break
                }
                resolved?.also { queryIdCache.put(cacheKey, it) }
            }
        } else {
            null
        }

        if (queryFilter != null) {
            url.addQueryParameter("${queryFilter.first}[0]", queryFilter.second.toString())
        } else if (trimmedQuery.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        hhLangId.forEachIndexed { index, it ->
            url.addQueryParameter("languages[${-index - 1}]", it.toString())
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", getSortPairs()[filter.state].second)

                is OrderFilter -> url.addQueryParameter("order", getOrderPairs()[filter.state].second)

                is DurationFilter -> url.addQueryParameter("duration", getDurationPairs()[filter.state].second)

                is AttributesGroupFilter -> filter.state.forEach {
                    if (it.state) url.addQueryParameter("attributes", it.value)
                }

                is StatusGroupFilter -> filter.state.forEach {
                    if (it.state) url.addQueryParameter("statuses", it.value)
                }

                is LookupFilter -> {
                    filter.state.split(",").map { it.trim() }.filter { it.isNotBlank() }.map {
                        lookupFilterId(it, filter.uri) ?: throw Exception("No ${filter.singularName} \"$it\" was found")
                    }.forEachIndexed { index, it ->
                        if (!(filter.uri == "languages" && hhLangId.contains(it))) {
                            url.addQueryParameter(filter.uri + "[$index]", it.toString())
                        }
                    }
                }

                else -> {}
            }
        }

        return parseMangasPage(client.get(url.build()))
    }

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.removePrefix("/en/comic/")
        if (!this.chapters) {
            // details and the single chapter come from the same endpoint
            val comic = client.get("$baseUrl/api/comics/$slug").body.string()
            return SMangaUpdate(
                comic.parseAs<MangaDetailsResponseDto>().toSMangaDetails(),
                listOf(comic.parseAs<ChapterResponseDto>().toSChapter()),
            )
        }

        return coroutineScope {
            val details = async { if (fetchDetails) client.get("$baseUrl/api/comics/$slug").parseAs<MangaDetailsResponseDto>().toSMangaDetails() else manga }
            val chapterList = async { if (fetchChapters) client.get("$baseUrl/api/comics/$slug/chapters").parseAs<ChapterListResponseDto>().map { it.toSChapter(slug) } else chapters }
            SMangaUpdate(details.await(), chapterList.await())
        }
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl/api/comics/${chapter.url}/images").parseAs<PageListResponseDto>().toPageList()

    // Authorization

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (username.isEmpty() or password.isEmpty()
            // image request doesn't need token
            or !request.url.toString().startsWith(baseUrl)
        ) {
            return chain.proceed(request)
        }

        if (token.isEmpty()) {
            token = this.login(chain, username, password)
        }
        val authRequest = request.newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authRequest)
    }

    private fun login(chain: Interceptor.Chain, username: String, password: String): String {
        val body = LoginRequestDto(username, password, rememberMe = true).toJsonRequestBody()
        val response = chain.proceed(POST("$baseUrl/api/login", headers, body))
        if (response.code == 401) {
            throw IOException("Failed to login, check if username and password are correct")
        }
        try {
            // Returns access token as a string, unless unparseable
            return response.parseAs<LoginResponseDto>().auth.access_token
        } catch (e: IllegalArgumentException) {
            throw IOException("Cannot parse login response body")
        }
    }

    private var token: String = ""
    private val username get() = getPrefUsername()
    private val password get() = getPrefPassword()

    // Preferences

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, username))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, password, true))
    }

    private fun PreferenceScreen.editTextPreference(title: String, default: String, value: String, isPassword: Boolean = false): EditTextPreference = EditTextPreference(context).apply {
        key = title
        this.title = title
        summary = value
        this.setDefaultValue(default)
        dialogTitle = title

        if (isPassword) {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    // Filters

    private class SortFilter(sortPairs: List<Pair<String, String>>) : Filter.Select<String>("Sort By", sortPairs.map { it.first }.toTypedArray())
    private class OrderFilter(orderPairs: List<Pair<String, String>>) : Filter.Select<String>("Order By", orderPairs.map { it.first }.toTypedArray())
    private class DurationFilter(durationPairs: List<Pair<String, String>>) : Filter.Select<String>("Duration", durationPairs.map { it.first }.toTypedArray())
    private class AttributeFilter(name: String, val value: String) : Filter.CheckBox(name)
    private class AttributesGroupFilter(attributePairs: List<Pair<String, String>>) : Filter.Group<AttributeFilter>("Attributes", attributePairs.map { AttributeFilter(it.first, it.second) })
    private class StatusFilter(name: String, val value: String) : Filter.CheckBox(name)
    private class StatusGroupFilter(attributePairs: List<Pair<String, String>>) : Filter.Group<StatusFilter>("Status", attributePairs.map { StatusFilter(it.first, it.second) })

    private class CategoriesFilter : LookupFilter("Categories", "categories", "category")
    private class TagsFilter : LookupFilter("Tags", "tags", "tag")
    private class ArtistsFilter : LookupFilter("Artists", "artists", "artist")
    private class GroupsFilter : LookupFilter("Groups", "groups", "group")
    private class CharactersFilter : LookupFilter("Characters", "characters", "character")
    private class ParodiesFilter : LookupFilter("Parodies", "parodies", "parody")
    private class LanguagesFilter : LookupFilter("Other Languages", "languages", "language")
    open class LookupFilter(name: String, val uri: String, val singularName: String) : Filter.Text(name)

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(getSortPairs()),
        OrderFilter(getOrderPairs()),
        DurationFilter(getDurationPairs()),
        Filter.Header("Separate terms with commas (,)"),
        CategoriesFilter(),
        TagsFilter(),
        ArtistsFilter(),
        GroupsFilter(),
        CharactersFilter(),
        ParodiesFilter(),
        LanguagesFilter(),
        AttributesGroupFilter(getAttributePairs()),
        StatusGroupFilter(getStatusPairs()),
    )

    private fun getSortPairs() = listOf(
        Pair("Upload Date", "uploaded_at"),
        Pair("Title", "title"),
        Pair("Pages", "pages"),
        Pair("Favorites", "favorites"),
        Pair("Popularity", "popularity"),
    )

    private fun getOrderPairs() = listOf(
        Pair("Descending", "desc"),
        Pair("Ascending", "asc"),
    )

    private fun getDurationPairs() = listOf(
        Pair("Today", "day"),
        Pair("This Week", "week"),
        Pair("This Month", "month"),
        Pair("This Year", "year"),
        Pair("All Time", "all"),
    )

    private fun getAttributePairs() = listOf(
        Pair("Translated", "translated"),
        Pair("Speechless", "speechless"),
        Pair("Rewritten", "rewritten"),
    )

    private fun getStatusPairs() = listOf(
        Pair("Ongoing", "ongoing"),
        Pair("Complete", "complete"),
        Pair("On Hold", "onhold"),
        Pair("Canceled", "canceled"),
    )

    companion object {
        private val QUERY_LOOKUP_URIS = listOf(
            "tags",
            "artists",
            "characters",
        )
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = ""
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""
    }
}
