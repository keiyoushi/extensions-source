package eu.kanade.tachiyomi.extension.en.comix

import android.content.SharedPreferences
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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Comix : HttpSource(), ConfigurableSource {

    override val name = "Comix"
    override val baseUrl = "https://comix.to"
    private val apiUrl = "https://comix.to/api/v2/"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    private fun parseSearchResponse(response: Response): MangasPage {
        val res: SearchResponse = response.parseAs()
        val manga =
            res.result.items.map { manga -> manga.toBasicSManga(preferences.posterQuality()) }
        return MangasPage(manga, res.result.pagination.page < res.result.pagination.lastPage)
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5, 2)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    private fun parseSearchResponse(response: Response): MangasPage {
        val res: SearchResponse = response.parseAs()
        val manga = res.result.items.map {
            it.toBasicSManga(preferences.posterQuality())
        }
        return MangasPage(manga, res.result.pagination.page < res.result.pagination.lastPage)
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("mangas")
            .addQueryParameter("order[views_30d]", "desc")
            .addQueryParameter("limit", "50")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) =
        parseSearchResponse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegment("mangas")
            .addQueryParameter("order[chapter_updated_at]", "desc")
            .addQueryParameter("limit", "50")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) =
        parseSearchResponse(response)

    // Search
    override fun getFilterList() = ComixFilters().getFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("mangas")

        filters.filterIsInstance<ComixFilters.UriFilter>()
            .forEach { it.addToUri(url) }

        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }

        url.addQueryParameter("limit", "50")
            .addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) =
        parseSearchResponse(response)

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("mangas")
            .addPathSegment(manga.url)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaResponse: SingleMangaResponse = response.parseAs()
        val manga = mangaResponse.result
        val terms = mutableListOf<Term>()

        if (manga.termIds.isNotEmpty()) {
            val base = apiUrl.toHttpUrl().newBuilder()
                .addPathSegment("terms")
                .addQueryParameter("limit", manga.termIds.size.toString())

            manga.termIds.forEach { id ->
                base.addQueryParameter("ids[]", id.toString())
            }

            ComixFilters.ApiTerms.values().forEach { apiTerm ->
                val req = GET(base.setQueryParameter("type", apiTerm.term).build(), headers)
                val resp = client.newCall(req).execute()
                    .parseAs<TermResponse>()

                terms.addAll(resp.result.items)
            }
        }

        if (terms.count() < manga.termIds.count()) {
            val termIdsSet = manga.termIds.map(Int::toString).toSet()

            val demographics = ComixFilters.getDemographics()
                .asSequence()
                .filter { (_, id) -> id in termIdsSet }
                .map { (name, id) -> Term(id.toInt(), "demographic", name, name, 0) }
                .toList()

            terms.addAll(demographics)
        }

        return manga.toSManga(preferences.posterQuality(), terms)
    }

    override fun getMangaUrl(manga: SManga): String =
        "$baseUrl/title${manga.url}"

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("mangas")
            .addPathSegment(manga.url)
            .addPathSegment("chapters")
            .addQueryParameter("order[number]", "desc")
            .addQueryParameter("limit", "100")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.code == 204) return emptyList()

        val result: ChapterResponse = response.parseAs()

        val chapters = result.result.items.toMutableList()
        val requestUrl = response.request.url

        var hasNextPage = result.result.pagination.lastPage > result.result.pagination.page
        var page = result.result.pagination.page

        while (hasNextPage) {
            val nextUrl = requestUrl.newBuilder()
                .addQueryParameter("page", (++page).toString())
                .build()

            val newResult = client.newCall(GET(nextUrl, headers)).execute()
                .parseAs<ChapterResponse>()

            chapters.addAll(newResult.result.items)
            hasNextPage = newResult.result.pagination.lastPage > newResult.result.pagination.page
        }

        val mangaHash = requestUrl.pathSegments[3]

        return chapters.map { it.toSChapter(mangaHash) }
    }

    // Reader
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val url = "${apiUrl}chapters/$chapterId"
        return GET(url, headers)
    }

    override fun getChapterUrl(chapter: SChapter) =
        "$baseUrl${chapter.url}"

    @kotlinx.serialization.Serializable
    private data class ComixChapterResponse(
        val status: Int,
        val result: ComixChapterResult?,
    )

    @kotlinx.serialization.Serializable
    private data class ComixChapterResult(
        val chapter_id: Int,
        val images: List<String>,
    )

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body!!.string()
        val res = json.decodeFromString<ComixChapterResponse>(body)
        val result = res.result ?: throw Exception("Chapter not found")

        if (result.images.isEmpty()) {
            throw Exception("No images found for chapter ${result.chapter_id}")
        }

        return result.images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    // Preferences
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

    private fun SharedPreferences.posterQuality() =
        getString(PREF_POSTER_QUALITY, "large")

    companion object {
        private const val PREF_POSTER_QUALITY = "pref_poster_quality"
    }
}
