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

    private fun searchRequest(page: Int, sort: String?): Request {
        val offset = (page - 1) * LIMIT
        val url = "$API_URL/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sort", sort ?: "")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaRequest(page: Int): Request = searchRequest(page, "rating")
    override fun latestUpdatesRequest(page: Int): Request = searchRequest(page, "newest")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // priority when query exists: usual filter > type filter, otherwise opposite
        val hasQuery = query.isNotBlank()
        val agsFilter = filters.filterIsInstance<AuthorGroupSeriesFilter>().firstOrNull()
        val agsValueFilter = filters.filterIsInstance<AuthorGroupSeriesValueFilter>().firstOrNull()
        val agsValue = agsValueFilter?.state?.trim()

        if (!hasQuery && agsFilter != null && agsFilter.state in agsFilter.values.indices) {
            val selected = agsFilter.values[agsFilter.state]
            val type = selected.key

            if (type.isNotBlank() && !agsValue.isNullOrBlank()) {
                // Search the input and pick a slug
                val url = "$API_URL/taxonomy/$type/".toHttpUrl().newBuilder()
                    .addQueryParameter("search", agsValue)
                    .addQueryParameter("limit", "1")
                    .build()

                val termRes = client.newCall(GET(url, headers)).execute()

                val slug = termRes.parseAs<TermsResult>().terms.firstOrNull()?.let { it.slug } ?: throw IOException("Failed to find $type")

                val url2 = "$API_URL/taxonomy/$type/$slug".toHttpUrl().newBuilder()
                    .addQueryParameter("limit", LIMIT.toString())
                    .addQueryParameter("page", page.toString())
                    .build()
                return GET(url2, headers)
            } else if (type.isBlank() && !agsValue.isNullOrBlank()) {
                throw IOException("Select a filter type")
            }
        }

        // Usual query search + other filters
        val offset = (page - 1) * LIMIT
        val builder = "$API_URL/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset.toString())

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

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = if (response.request.url.pathSegments.contains("manga")) {
            response.parseAs<List<MangaItem>>()
        } else {
            response.parseAs<TaxonomyMangas>().mangaList
        }
        return MangasPage(mangas.map { it.toSManga() }, hasNextPage = mangas.isNotEmpty())
    }

    override fun getFilterList() = FilterList(
        Filter.Separator(),
        StatusList(statusList),
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList(getGenreList()),
        Filter.Separator(),
        Filter.Header("Type Filter Is Ignored When Searching"),
        AuthorGroupSeriesFilter(authorGroupSeriesOptions),
        AuthorGroupSeriesValueFilter(),
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

    fun SChapter.getIdOrError(): String = if (!url.startsWith('/')) url else throw IOException("Refresh to reload chapters.")

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
