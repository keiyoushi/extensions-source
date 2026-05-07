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
import keiyoushi.utils.getPreferencesLazy
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
    private val apiUrl = "https://comix.to/api/v1"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("order[score]", "desc")
            addQueryParameter("limit", "28")
            addQueryParameter("page", page.toString())

            if (preferences.hideNsfw()) {
                NSFW_GENRE_IDS.forEach {
                    addQueryParameter("genres_ex[]", it)
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("order[chapter_updated_at]", "desc")
            addQueryParameter("limit", "28")
            addQueryParameter("page", page.toString())

            if (preferences.hideNsfw()) {
                NSFW_GENRE_IDS.forEach {
                    addQueryParameter("genres_ex[]", it)
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ============================== Search ===============================
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

            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
                removeAllQueryParameters("order[score]")
                removeAllQueryParameters("order[chapter_updated_at]")
                addQueryParameter("order[relevance]", "desc")
            }

            if (preferences.hideNsfw()) {
                NSFW_GENRE_IDS.forEach {
                    addQueryParameter("genres_ex[]", it)
                }
            }

            addQueryParameter("limit", "28")
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
            return MangasPage(manga, res.result.hasNextPage())
        }
    }

    // ============================== Filters ==============================
    override fun getFilterList() = Filters().getFilterList()

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val hid = manga.url.removePrefix("/").substringBefore("-")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(hid)
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title${manga.url}"

    // ============================= Chapters ==============================
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val hid = manga.url.removePrefix("/").substringBefore("-")
        val fullSlug = manga.url.removePrefix("/")
        return chapterListRequest(hid, fullSlug, 1)
    }

    private fun chapterListRequest(mangaHash: String, mangaSlug: String, page: Int): Request {
        val path = "/manga/$mangaHash/chapters"
        val hashToken = generateHash(path)
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(mangaHash)
            .addPathSegment("chapters")
            .addQueryParameter("order[number]", "desc")
            .addQueryParameter("limit", "100")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_", hashToken)
            .addQueryParameter("mangaSlug", mangaSlug) // Add slug as query parameter
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val deduplicate = preferences.deduplicateChapters()
        val mangaHash = response.request.url.pathSegments[3]
        val mangaSlug = response.request.url.queryParameter("mangaSlug") ?: mangaHash // Extract slug
        var resp: ChapterDetailsResponse = response.parseAs()

        var chapterMap: LinkedHashMap<Number, Chapter>? = null
        var chapterList: ArrayList<Chapter>? = null

        if (deduplicate) {
            chapterMap = LinkedHashMap()
            deduplicateChapters(chapterMap, resp.result.items)
        } else {
            chapterList = ArrayList(resp.result.items)
        }

        var page = 2
        var hasNext = resp.result.hasNextPage()

        while (hasNext) {
            resp = client
                .newCall(chapterListRequest(mangaHash, mangaSlug, page++))
                .execute()
                .parseAs()

            val items = resp.result.items

            if (deduplicate) {
                deduplicateChapters(chapterMap!!, items)
            } else {
                chapterList!!.addAll(items)
            }
            hasNext = resp.result.hasNextPage()
        }

        val finalChapters: List<Chapter> =
            if (deduplicate) {
                chapterMap!!.values.toList()
            } else {
                chapterList!!
            }

        return finalChapters.map { it.toSChapter(mangaSlug) }
    }

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
                val newIsOfficial = ch.isOfficial
                val currentIsOfficial = current.isOfficial
                val newIsGroup10702 = ch.group?.id == 10702
                val currentIsGroup10702 = current.group?.id == 10702

                val better = when {
                    newIsOfficial && !currentIsOfficial -> true
                    !newIsOfficial && currentIsOfficial -> false
                    newIsGroup10702 && !currentIsGroup10702 -> true
                    !newIsGroup10702 && currentIsGroup10702 -> false
                    else -> when {
                        ch.votes > current.votes -> true
                        ch.votes < current.votes -> false
                        else -> ch.id > current.id
                    }
                }
                if (better) chapterMap[key] = ch
            }
        }
    }

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/").substringBefore("-")
        val path = "/chapters/$chapterId"
        val hashToken = generateHash(path)
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("chapters")
            .addPathSegment(chapterId)
            .addQueryParameter("_", hashToken)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res: ChapterResponse = response.parseAs()
        val result = res.result ?: throw Exception("Chapter not found")

        if (result.pages.isEmpty()) {
            throw Exception("No images found for chapter ${result.id}")
        }

        return result.pages.mapIndexed { index, img ->
            Page(index, imageUrl = img.url)
        }
    }

    // ============================= Settings =============================
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
