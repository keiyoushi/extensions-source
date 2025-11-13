package eu.kanade.tachiyomi.extension.en.comix

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Comix : HttpSource(), ConfigurableSource {

    override val name = "Comix"
    override val baseUrl = "https://comix.to/"
    private val apiUrl = "https://comix.to/api/v2/"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    private val json: Json by injectLazy()

    private fun parseSearchResponse(response: Response): MangasPage {
        val res: SearchResponse = response.parseAs()
        val manga =
            res.result.items.map { manga -> manga.toBasicSManga(preferences.posterQuality()) }
        return MangasPage(manga, res.result.pagination.page < res.result.pagination.lastPage)
    }

    override val client = network.cloudflareClient.newBuilder().rateLimit(5, 2).build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", baseUrl)

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    /******************************* POPULAR MANGA ************************************/
    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegment("mangas")
            .addQueryParameter("order[views_30d]", "desc")
            .addQueryParameter("limit", "28")
            .addQueryParameter("page", page.toString()).build()

        Log.d("comix", url.toString())

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseSearchResponse(response)

    /******************************* LATEST MANGA ************************************/
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegment("mangas")
            .addQueryParameter("order[chapter_updated_at]", "desc").addQueryParameter("limit", "28")
            .addQueryParameter("page", page.toString()).build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseSearchResponse(response)

    /******************************* SEARCHING ***************************************/
    override fun getFilterList() = ComixFilters().getFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegment("mangas")
        filters.filterIsInstance<ComixFilters.UriFilter>().forEach { it.addToUri(url) }

        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }

        url.addQueryParameter("limit", "28")
            .addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSearchResponse(response)

    /******************************* Single Manga Page *******************************/
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegment("mangas").addPathSegment(manga.url)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaResponse: SingleMangaResponse = response.parseAs()
        val manga = mangaResponse.result
        val terms = mutableListOf<Term>()

        // Check the manga's term ids against all terms to match them and aggregate the results
        // (Genres, Artists, Authors, etc)
        if (manga.termIds.isNotEmpty()) {
            val termsUrlBuilder = apiUrl.toHttpUrl().newBuilder().addPathSegment("terms")
                .addQueryParameter("limit", manga.termIds.size.toString())

            manga.termIds.forEach { id ->
                termsUrlBuilder.addQueryParameter("ids[]", id.toString())
            }

            // Query each term type, because Comix doesn't support checking everything within one request
            ComixFilters.ApiTerms.values().forEach { apiTerm ->
                val termsRequest =
                    GET(termsUrlBuilder.setQueryParameter("type", apiTerm.term).build(), headers)
                val termsResponse = client.newCall(termsRequest).execute().parseAs<TermResponse>()

                if (termsResponse.result.items.isNotEmpty()) {
                    terms.addAll(termsResponse.result.items)
                }
            }
        }

        // Check if we are missing demographics
        if (terms.count() < manga.termIds.count()) {
            val dems = ComixFilters.getDemographics().filter { (_, id) -> manga.termIds.contains(id.toInt()) }
                .map { (name, id) -> Term(id.toInt(), "demographic", name, name, 0) }
            terms.addAll(dems)
        }

        return manga.toSManga(preferences.posterQuality(), terms)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/title${manga.url}"
    }

    /******************************* Chapters List *******************************/
    override fun chapterListRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegment("mangas").addPathSegment(manga.url)
            .addPathSegment("chapters").addQueryParameter("order[number]", "desc")
            .addQueryParameter("limit", "100").build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.code == 204) {
            return emptyList()
        }

        val result: ChapterResponse = response.parseAs()

        val chapters = result.result.items.toMutableList()
        val requestUrl = response.request.url
        val mangaHashId = requestUrl.pathSegments[3].removePrefix("/")

        var hasNextPage = result.result.pagination.lastPage > result.result.pagination.page
        var page = result.result.pagination.page

        while (hasNextPage) {
            val url = requestUrl.newBuilder().addQueryParameter("page", (++page).toString()).build()

            val newResponse = client.newCall(GET(url, headers)).execute()

            val newResult: ChapterResponse = newResponse.parseAs()

            chapters.addAll(newResult.result.items)

            hasNextPage = newResult.result.pagination.lastPage > newResult.result.pagination.page
        }

        return chapters.map { chapter -> chapter.toSChapter(mangaHashId) }
    }

    /******************************* Page List (Reader) ************************************/
    override fun pageListRequest(chapter: SChapter): Request {
        return super.pageListRequest(chapter)
    }

    // Doesn't work cause Next.js
    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val data = doc.select("div.viewer-wrapper > div.read-viewer.longsrtip > div.page > img")

        if (data.isEmpty()) {
            Log.d("comix", "data is null")
            throw Exception("Could not parse reader page")
        }

        return data.mapIndexed { index, element -> Page(index, imageUrl = element.attr("src")) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/${chapter.url}"
    }

    /******************************* PREFERENCES ************************************/
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_POSTER_QUALITY
            title = "Thumbnail Quality"
            summary = "Change the quality of the thumbnail images. Large is the default."
            entryValues = arrayOf("small", "medium", "large")
            entries = arrayOf("Small", "Medium", "Large")
            setDefaultValue("large")
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.posterQuality() = getString(PREF_POSTER_QUALITY, "large")

    companion object {
        private const val PREF_POSTER_QUALITY = "pref_poster_quality"
    }
}
