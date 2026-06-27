package eu.kanade.tachiyomi.extension.id.doujindesu

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.LinkedHashMap

class Doujindesu :
    HttpSource(),
    ConfigurableSource {

    override val id = 7704282043609669342L

    override val name = "Doujindesu"

    private val defaultBaseUrl = "https://doujin.desu.xxx"

    override val baseUrl by lazy { getDefaultBaseUrl() }

    private val apiUrl: String
        get() = "$baseUrl/api"

    override val lang = "id"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    val decryptor = Decryptor(apiUrl)

    override val client = super.client.newBuilder()
        .addInterceptor(decryptor.xorInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val headers = request.headers.newBuilder()

            if (imageDomains.any { url.contains(it) }) {
                headers.removeAll("x-app-secret")
            }

            chain.proceed(request.newBuilder().headers(headers.build()).build())
        }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("x-app-secret", APP_SECRET)
        .add("Referer", "$baseUrl/")

    private fun searchRequest(page: Int, sort: String = "latest_chapter"): Request {
        val offset = (page - 1) * LIMIT
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sort", sort)
            .fragment(page.toString()) // for parse to know end of pagination
            .build()
        return GET(url, headers)
    }

    override fun popularMangaRequest(page: Int): Request = searchRequest(page, "rating")
    override fun latestUpdatesRequest(page: Int): Request = searchRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    private val slugCache = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 30
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // priority when query exists: usual filter > type filter, otherwise opposite
        val hasQuery = query.isNotBlank()
        val agsFilter = filters.filterIsInstance<AuthorGroupSeriesFilter>().firstOrNull()
        val agsValueFilter = filters.filterIsInstance<AuthorGroupSeriesValueFilter>().firstOrNull()
        val agsValue = agsValueFilter?.state?.trim()

        if (!hasQuery && agsFilter != null && agsFilter.state in agsFilter.values.indices) {
            val selected = agsFilter.values[agsFilter.state]
            val type = selected.key
            val cacheKey = "$type:$agsValue"

            if (type.isNotBlank() && !agsValue.isNullOrBlank()) {
                // Search the input and pick a slug if not cached
                val slug = slugCache[cacheKey] ?: run {
                    val url = "$apiUrl/taxonomy/$type/".toHttpUrl().newBuilder()
                        .addQueryParameter("search", agsValue)
                        .addQueryParameter("limit", "1")
                        .build()
                    val termRes = client.newCall(GET(url, headers)).execute()
                    termRes.parseAs<TermsResult>().terms.firstOrNull()?.slug
                        ?: throw IOException("Gagal menemukan: $agsValue")
                }.also { slugCache[cacheKey] = it }

                val url2 = "$apiUrl/taxonomy/$type/$slug".toHttpUrl().newBuilder()
                    .addQueryParameter("limit", LIMIT.toString())
                    .addQueryParameter("page", page.toString())
                    .build()
                return GET(url2, headers)
            } else if (type.isBlank() && !agsValue.isNullOrBlank()) {
                throw IOException("Pilih tipe filter")
            }
        }

        // Usual query search + other filters
        val builder = searchRequest(page).url.newBuilder()

        if (hasQuery) builder.addQueryParameter("search", query)

        filters.forEach { filter ->
            when (filter) {
                is StatusList -> {
                    if (filter.state in filter.values.indices) {
                        filter.values[filter.state].key.takeIf { it.isNotBlank() }
                            ?.let { builder.addQueryParameter("status", it) }
                    }
                }
                is CategoryNames -> {
                    if (filter.state in filter.values.indices) {
                        filter.values[filter.state].key.takeIf { it.isNotBlank() }
                            ?.let { builder.addQueryParameter("type", it) }
                    }
                }
                is OrderBy -> {
                    if (filter.state in filter.values.indices) {
                        filter.values[filter.state].key.takeIf { it.isNotBlank() }
                            ?.let { builder.addQueryParameter("sort", it) }
                    }
                }
                is GenreList -> {
                    val selected = filter.state.filter { it.state }
                    if (selected.isNotEmpty()) {
                        builder.addEncodedQueryParameter("genre", selected.joinToString(",") { it.id.lowercase().replace(" ", "-") })
                    }
                }
                else -> {}
            }
        }

        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url

        var hasNextPage = false

        val mangas = if (url.pathSegments.contains("manga")) {
            val total = response.headers["x-total-count"]?.toIntOrNull()
            val currentPage = url.fragment?.toIntOrNull() ?: 1
            hasNextPage = total?.let { currentPage * LIMIT < it } ?: true
            response.parseAs<List<MangaItem>>()
        } else {
            val taxonomyDto = response.parseAs<TaxonomyMangas>()
            hasNextPage = taxonomyDto.pagination.page < taxonomyDto.pagination.totalPages
            taxonomyDto.mangaList
        }
        return MangasPage(mangas.map { it.toSManga() }, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filter Tipe Diabaikan Saat Menggunakan Pencarian"),
        AuthorGroupSeriesFilter(authorGroupSeriesOptions),
        AuthorGroupSeriesValueFilter(),
        Filter.Separator(),
        StatusList(statusList),
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList(getGenreList()),
    )

    // Detail Parse
    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.getSlug()}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.getSlug()
        return GET("$apiUrl/manga/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaItem>().toSManga()

    // Chapter Stuff
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<MangaItem>().chapters.map { it.toSChapter() }

    // More parser stuff
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.split("/").last { it.isNotBlank() }
        return GET("$apiUrl/chapters/$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PageList>().pages.mapIndexed { i, imgUrl ->
        Page(i, imageUrl = Parser.unescapeEntities(imgUrl, false))
    }

    fun SManga.getSlug(): String {
        val path = if (url.startsWith("http")) {
            url.toHttpUrl().encodedPath
        } else {
            url
        }
        return path.split("/").last { it.isNotBlank() }
    }

    companion object {
        private const val APP_SECRET = "dfdf72051dbfdc7d76889ebd31324e74"
        private const val LIMIT = 24

        private val imageDomains = listOf("desu.photos", "cdn-static.desu.xxx", "desu.pics", "uploads", "upload")

        private const val PREF_BASE_URL = "defaultBaseUrl"
        private const val PREF_CUSTOM_BASE_URL = "customBaseUrl"
        private const val PREF_BASE_URL_TITLE = "Mengganti BaseUrl"
        private const val PREF_BASE_URL_SUMMARY = "Mengganti domain default dengan domain yang berbeda"
        private const val RESTART_MSG = "Mulai ulang aplikasi untuk menerapkan pengaturan baru"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref =
            EditTextPreference(screen.context).apply {
                key = PREF_CUSTOM_BASE_URL
                title = PREF_BASE_URL_TITLE
                summary = PREF_BASE_URL_SUMMARY
                this.setDefaultValue(defaultBaseUrl)
                dialogTitle = PREF_BASE_URL_TITLE
                dialogMessage = "Default: $defaultBaseUrl"

                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, RESTART_MSG, Toast.LENGTH_LONG).show()
                    true
                }
            }
        screen.addPreference(baseUrlPref)
    }

    private fun getDefaultBaseUrl(): String = preferences.getString(PREF_CUSTOM_BASE_URL, defaultBaseUrl)!!

    init {
        preferences.getString(PREF_BASE_URL, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                preferences
                    .edit()
                    .putString(PREF_CUSTOM_BASE_URL, defaultBaseUrl)
                    .putString(PREF_BASE_URL, defaultBaseUrl)
                    .apply()
            }
        }
    }
}
