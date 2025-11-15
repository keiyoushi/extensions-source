package eu.kanade.tachiyomi.extension.en.comix

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import rx.Observable

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
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    private fun parseMangasResponse(response: Response): MangasPage {
        val res: SearchResponse = response.parseAs()
        val manga = res.result.items.map {
            it.toBasicSManga(preferences.posterQuality())
        }
        return MangasPage(manga, res.result.pagination.page < res.result.pagination.lastPage)
    }

    /******************************* POPULAR MANGA ************************************/
    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("order[views_30d]", "desc")
            .addQueryParameter("limit", "50")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) =
        parseMangasResponse(response)

    /******************************* LATEST MANGA ************************************/
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("order[chapter_updated_at]", "desc")
            .addQueryParameter("limit", "50")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) =
        parseMangasResponse(response)

    /******************************* SEARCHING ***************************************/
    override fun getFilterList() = ComixFilters().getFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")

        filters.filterIsInstance<ComixFilters.UriFilter>()
            .forEach { it.addToUri(url) }

        // Make searches accurate
        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
            url.removeAllQueryParameters("order[views_30d]")
            url.setQueryParameter("order[relevance]", "desc")
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
            .addPathSegment("manga")
            .addPathSegment(manga.url)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaResponse: SingleMangaResponse = response.parseAs()
        val manga = mangaResponse.result
        val terms = mutableListOf<Term>()
        val ids = manga.termIds.toMutableList()

        if (ids.isEmpty()) {
            return manga.toSManga(
                preferences.posterQuality(),
                preferences.alternativeNamesInDescription(),
            )
        }

        // Check for demographics and genres
        terms.addAll(
            ComixFilters
                .getDemographics()
                .asSequence()
                .filter { (_, id) -> id.toInt() in ids }
                .map { (name, id) -> Term(id.toInt(), "demographic", name, name, 0) },
        )
        terms.addAll(
            ComixFilters
                .getGenres()
                .asSequence()
                .filter { (_, id) -> id.toInt() in ids }
                .map { (name, id) -> Term(id.toInt(), "genre", name, name, 0) },
        )
        ids.removeAll { it in terms.map(Term::termId).toSet() }

        // Check the manga's term ids against all terms to match them and aggregate the results
        // (Genres, Artists, Authors, etc)
        val base = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("terms")
            .addQueryParameter("limit", manga.termIds.size.toString())

        ids.forEach { base.addQueryParameter("ids[]", it.toString()) }

        // Query for artists and authors individually
        // because Comix doesn't support checking everything within one request
        ComixFilters.ApiTerms.values().forEach { apiTerm ->
            val req = GET(base.setQueryParameter("type", apiTerm.term).build(), headers)
            val resp = client.newCall(req).execute()
                .parseAs<TermResponse>()

            terms.addAll(resp.result.items)
            ids.removeAll { it in resp.result.items.map(Term::termId) }
        }

        return manga.toSManga(
            preferences.posterQuality(),
            preferences.alternativeNamesInDescription(),
            terms,
        )
    }

    override fun getMangaUrl(manga: SManga): String =
        "$baseUrl/title${manga.url}"

    /******************************* Chapters List *******************************/
    override fun getChapterUrl(chapter: SChapter) =
        "$baseUrl/${chapter.url}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.fromCallable {
            val deduplicate = preferences.deduplicateChapters()
            val mangaHash = manga.url.substringAfterLast("/")

            // When deduplication is enabled store only the best chapter per number.
            val chapterMap: LinkedHashMap<Number, Chapter>? =
                if (deduplicate) LinkedHashMap() else null
            // When disabled just accumulate all.
            val chapterList: ArrayList<Chapter>? =
                if (!deduplicate) ArrayList() else null

            var page = 1
            var hasNext: Boolean

            do {
                val resp: ChapterDetailsResponse = client
                    .newCall(chapterListRequest(manga, page++))
                    .execute()
                    .parseAs()

                val items = resp.result.items
                hasNext = resp.result.pagination.lastPage > resp.result.pagination.page

                if (deduplicate) {
                    for (ch in items) {
                        val key = ch.number
                        val current = chapterMap!![key]
                        if (current == null) {
                            chapterMap[key] = ch
                        } else {
                            // Prefer official scan group
                            val officialNew = ch.scanlationGroupId == 9275
                            val officialCurrent = current.scanlationGroupId == 9275
                            val better = when {
                                officialNew && !officialCurrent -> true
                                !officialNew && officialCurrent -> false
                                // compare votes then updatedAt
                                else -> when {
                                    ch.votes > current.votes -> true
                                    ch.votes < current.votes -> false
                                    else -> ch.updatedAt > current.updatedAt
                                }
                            }
                            if (better) chapterMap[key] = ch
                        }
                    }
                } else {
                    chapterList!!.addAll(items)
                }
            } while (hasNext)

            val finalChapters: List<Chapter> =
                if (deduplicate) {
                    chapterMap!!.values.toList()
                } else {
                    chapterList!!
                }

            finalChapters.map { it.toSChapter(mangaHash) }
        }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(manga.url)
            .addPathSegment("chapters")
            .addQueryParameter("order[number]", "desc")
            .addQueryParameter("limit", "100")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException()

    /******************************* Page List (Reader) ************************************/
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val url = "${apiUrl}chapters/$chapterId"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res: ChapterResponse = response.parseAs()
        val result = res.result ?: throw Exception("Chapter not found")

        if (result.images.isEmpty()) {
            throw Exception("No images found for chapter ${result.chapterId}")
        }

        return result.images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    /******************************* PREFERENCES ************************************/
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_POSTER_QUALITY
            title = "Thumbnail Quality"
            summary = "Change the quality of the thumbnail. Current: %s."
            entryValues = arrayOf("small", "medium", "large")
            entries = arrayOf("Small", "Medium", "Large")
            setDefaultValue("large")
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DEDUPLICATE_CHAPTERS
            title = "Deduplicate Chapters"
            summary = "Remove duplicate chapters from the chapter list.\n" +
                "Official chapters (Comix-marked) are preferred, followed by the highest-voted or most recent.\n" +
                "Warning: It can be slow on large lists."

            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = ALTERNATIVE_NAMES_IN_DESCRIPTION
            title = "Show Alternative Names in Description"

            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.posterQuality() =
        getString(PREF_POSTER_QUALITY, "large")

    private fun SharedPreferences.deduplicateChapters() =
        getBoolean(DEDUPLICATE_CHAPTERS, false)

    private fun SharedPreferences.alternativeNamesInDescription() =
        getBoolean(ALTERNATIVE_NAMES_IN_DESCRIPTION, false)

    companion object {
        private const val PREF_POSTER_QUALITY = "pref_poster_quality"
        private const val DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private const val ALTERNATIVE_NAMES_IN_DESCRIPTION = "pref_alt_names_in_description"
    }
}
