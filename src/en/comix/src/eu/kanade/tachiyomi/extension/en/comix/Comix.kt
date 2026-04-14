package eu.kanade.tachiyomi.extension.en.comix

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.comix.Hash.generateHash
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response

class Comix :
    HttpSource(),
    ConfigurableSource {

    override val name = "Comix"
    override val baseUrl = "https://comix.to"
    private val apiUrl = "https://comix.to/api/v2"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("order[views_30d]", "desc")
            addQueryParameter("limit", "50")
            addQueryParameter("page", page.toString())

            if (preferences.hideNsfw()) {
                NSFW_GENRE_IDS.forEach {
                    addQueryParameter("genres[]", "-$it")
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("order[chapter_updated_at]", "desc")
            addQueryParameter("limit", "50")
            addQueryParameter("page", page.toString())

            if (preferences.hideNsfw()) {
                NSFW_GENRE_IDS.forEach {
                    addQueryParameter("genres[]", "-$it")
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ========================= Search =========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val queryUrl = query.trim().toHttpUrlOrNull()
            if (queryUrl != null) {
                val host = queryUrl.host.removePrefix("www.")
                if (host == baseUrl.toHttpUrl().host.removePrefix("www.") && queryUrl.pathSegments.size >= 2 && queryUrl.pathSegments[0] == "title") {
                    val mangaId = queryUrl.pathSegments[1].substringBefore("-")
                    return mangaDetailsRequest(SManga.create().apply { this.url = "/$mangaId" })
                }
            }
        }

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")

            filters.filterIsInstance<Filters.UriFilter>()
                .forEach { it.addToUri(this) }

            // Make searches accurate
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
                removeAllQueryParameters("order[views_30d]")
                setQueryParameter("order[relevance]", "desc")
            }

            if (preferences.hideNsfw()) {
                NSFW_GENRE_IDS.forEach {
                    addQueryParameter("genres[]", "-$it")
                }
            }

            addQueryParameter("limit", "50")
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val posterQuality = preferences.posterQuality()

        val pathSegments = response.request.url.pathSegments
        val mangaIdx = pathSegments.indexOf("manga")

        if (mangaIdx != -1 && pathSegments.size > mangaIdx + 1 && !pathSegments.contains("chapters")) {
            val res: SingleMangaResponse = response.parseAs()
            val manga = listOf(res.result.toBasicSManga(posterQuality))
            return MangasPage(manga, false)
        } else {
            val res: SearchResponse = response.parseAs()
            val manga = res.result.items.map { it.toBasicSManga(posterQuality) }
            return MangasPage(manga, res.result.pagination.page < res.result.pagination.lastPage)
        }
    }

    // ========================= Filters =========================
    override fun getFilterList() = Filters().getFilterList()

    // ========================= Details =========================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(manga.url)
            .addQueryParameter("includes[]", "demographic")
            .addQueryParameter("includes[]", "genre")
            .addQueryParameter("includes[]", "theme")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .addQueryParameter("includes[]", "publisher")
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaResponse: SingleMangaResponse = response.parseAs()

        return mangaResponse.result.toSManga(
            preferences.posterQuality(),
            preferences.alternativeNamesInDescription(),
            preferences.scorePosition(),
        )
    }

    // ========================= Chapters =========================
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga.url.removePrefix("/"), 1)

    private fun chapterListRequest(mangaHash: String, page: Int): Request {
        val path = "/manga/$mangaHash/chapters"
        val time = 1L
        val hashToken = generateHash(path, 0, time)
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(mangaHash)
            .addPathSegment("chapters")
            .addQueryParameter("order[number]", "desc")
            .addQueryParameter("limit", "100")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("time", time.toString())
            .addQueryParameter("_", hashToken)
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val deduplicate = preferences.deduplicateChapters()
        val mangaHash = response.request.url.pathSegments[3]
        var resp: ChapterDetailsResponse = response.parseAs()

        // When deduplication is enabled store only the best chapter per number.
        var chapterMap: LinkedHashMap<Number, Chapter>? = null
        // When disabled just accumulate all.
        var chapterList: ArrayList<Chapter>? = null

        if (deduplicate) {
            chapterMap = LinkedHashMap()
            deduplicateChapters(chapterMap, resp.result.items)
        } else {
            chapterList = ArrayList(resp.result.items)
        }

        var page = 2
        var hasNext: Boolean

        do {
            resp = client
                .newCall(chapterListRequest(mangaHash, page++))
                .execute()
                .parseAs()

            val items = resp.result.items
            hasNext = resp.result.pagination.lastPage > resp.result.pagination.page

            if (deduplicate) {
                deduplicateChapters(chapterMap!!, items)
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

        return finalChapters.map { it.toSChapter(mangaHash) }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title${manga.url}"

    private fun deduplicateChapters(
        chapterMap: LinkedHashMap<Number, Chapter>,
        items: List<Chapter>,
    ) {
        for (ch in items) {
            val key = ch.number
            val current = chapterMap[key]
            if (current == null) {
                chapterMap[key] = ch
            } else {
                // Prefer official flag first, then "Official?" group, then votes/updatedAt
                val newIsOfficial = ch.isOfficial == 1
                val currentIsOfficial = current.isOfficial == 1
                val newIsGroup10702 = ch.scanlationGroupId == 10702
                val currentIsGroup10702 = current.scanlationGroupId == 10702

                val better = when {
                    // Prefer official-marked chapters
                    newIsOfficial && !currentIsOfficial -> true
                    !newIsOfficial && currentIsOfficial -> false

                    // If neither official, prefer group "Official?"
                    newIsGroup10702 && !currentIsGroup10702 -> true
                    !newIsGroup10702 && currentIsGroup10702 -> false

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
    }

    // ========================= Pages =========================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("chapters")
            .addPathSegment(chapterId)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res: ChapterResponse = response.parseAs()
        val result = res.result ?: throw Exception("Chapter not found")

        if (result.images.isEmpty()) {
            throw Exception("No images found for chapter ${result.chapterId}")
        }

        return result.images.mapIndexed { index, img ->
            Page(index, imageUrl = img.url)
        }
    }

    // ========================= Settings =========================
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
            key = NSFW_PREF
            title = "Hide NSFW content"
            summary = "Hides NSFW content from popular, latest, and search lists."
            setDefaultValue(true)
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

        ListPreference(screen.context).apply {
            key = PREF_SCORE_POSITION
            title = "Score display position"
            summary = "%s"
            entries = arrayOf("Top of description", "Bottom of description", "Don't show")
            entryValues = arrayOf("top", "bottom", "none")
            setDefaultValue("top")
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.posterQuality() = getString(PREF_POSTER_QUALITY, "large")

    private fun SharedPreferences.deduplicateChapters() = getBoolean(DEDUPLICATE_CHAPTERS, false)

    private fun SharedPreferences.alternativeNamesInDescription() = getBoolean(ALTERNATIVE_NAMES_IN_DESCRIPTION, false)

    private fun SharedPreferences.scorePosition() = getString(PREF_SCORE_POSITION, "top") ?: "top"

    private fun SharedPreferences.hideNsfw() = getBoolean(NSFW_PREF, true)

    companion object {
        private const val PREF_POSTER_QUALITY = "pref_poster_quality"
        private const val NSFW_PREF = "nsfw_pref"
        private const val DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private const val ALTERNATIVE_NAMES_IN_DESCRIPTION = "pref_alt_names_in_description"
        private const val PREF_SCORE_POSITION = "pref_score_position"

        private val NSFW_GENRE_IDS = listOf("87264", "8", "87265", "13", "87266", "87268")
    }
}
