package eu.kanade.tachiyomi.extension.id.doujindesu

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
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
import java.io.IOException

private const val DOMAIN = "doujin.desu.xxx"

class DoujinDesu :
    HttpSource(),
    ConfigurableSource {

    override val name = "Doujindesu"
    override val baseUrl by lazy { preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!! }
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    val decryptor = Decryptor(API_URL)

    override val client = super.client.newBuilder()
        .addInterceptor(decryptor.xorInterceptor())
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("x-app-secret", APP_SECRET)
        .add("Referer", "$baseUrl/")

    private fun searchRequest(page: Int, sort: String = "latest_chapter"): Request {
        val offset = (page - 1) * LIMIT
        val url = "$API_URL/manga".toHttpUrl().newBuilder()
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

    private val slugCache = mutableMapOf<String, String>()

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
                    val url = "$API_URL/taxonomy/$type/".toHttpUrl().newBuilder()
                        .addQueryParameter("search", agsValue)
                        .addQueryParameter("limit", "1")
                        .build()
                    val termRes = client.newCall(GET(url, headers)).execute()
                    termRes.parseAs<TermsResult>().terms.firstOrNull()?.slug
                        ?: throw IOException("Gagal menemukan: $agsValue")
                }.also { slugCache[cacheKey] = it }

                val url2 = "$API_URL/taxonomy/$type/$slug".toHttpUrl().newBuilder()
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

    override fun mangaDetailsRequest(manga: SManga) = GET("$API_URL/manga/${manga.getSlug()}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaItem>().toSManga()

    // Chapter Stuff
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<MangaItem>().chapters.map { it.toSChapter() }

    // More parser stuff
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request = GET("$API_URL/chapters/${chapter.getIdOrError()}", headers)

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PageList>().pages.mapIndexed { i, imgUrl ->
        Page(i, imageUrl = imgUrl)
    }

    fun SManga.getSlug() = url.removePrefix("/manga/").removeSuffix("/")

    fun SChapter.getIdOrError(): String = if (!url.startsWith('/')) url else throw IOException("Segarkan untuk memuat ulang bab.")

    companion object {
        private const val APP_SECRET = "dfdf72051dbfdc7d76889ebd31324e74"
        private const val API_URL = "https://$DOMAIN/api"
        private const val LIMIT = 24

        private val PREF_DOMAIN_KEY = "preferred_domain_name_v${AppInfo.getVersionName()}"
        private const val PREF_DOMAIN_TITLE = "Mengganti BaseUrl"
        private const val PREF_DOMAIN_DEFAULT = "https://$DOMAIN"
        private const val PREF_DOMAIN_SUMMARY = "Mengganti domain default dengan domain yang berbeda"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = PREF_DOMAIN_TITLE
            dialogTitle = PREF_DOMAIN_TITLE
            summary = PREF_DOMAIN_SUMMARY
            dialogMessage = "Default: $PREF_DOMAIN_DEFAULT"
            setDefaultValue(PREF_DOMAIN_DEFAULT)

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Mulai ulang aplikasi untuk menerapkan pengaturan baru.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }
}
