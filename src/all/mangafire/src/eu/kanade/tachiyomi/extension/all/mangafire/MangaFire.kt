package eu.kanade.tachiyomi.extension.all.mangafire

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class MangaFire :
    KeiSource(),
    ConfigurableSource {

    private val langCode: String
        get() = when (lang) {
            "es-419" -> "es-la"
            "pt-BR" -> "pt-br"
            else -> lang
        }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
        addInterceptor(VrfSigner().interceptor())
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept", "application/json")
    }

    private val preferences = getPreferences()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("order[views_30d]", "desc")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "50")
            ContentRatingFilter(contentRating).addToUri(this)
        }.build()
        return client.get(url).use { response ->
            parseMangaList(response)
        }
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("order[chapter_updated_at]", "desc")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "50")
            ContentRatingFilter(contentRating).addToUri(this)
        }.build()
        return client.get(url).use { response ->
            parseMangaList(response)
        }
    }

    private fun parseMangaList(response: Response): MangasPage {
        val data = response.parseAs<ApiResponse<MangaDto>>()
        val mangas = data.items.map { it.toSManga() }
        return MangasPage(mangas, data.meta?.hasNext ?: false)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val authorQuery = filters.firstInstanceOrNull<AuthorFilter>()?.state.orEmpty()
        var authorId: String? = null

        if (authorQuery.isNotBlank()) {
            val tags = client.get("$baseUrl/api/tags?keyword=$authorQuery").parseAs<TagResponse>()

            authorId = tags.data.firstOrNull { it.type == "author" || it.type == "artist" }?.id?.toString()

            if (authorId == null) {
                return MangasPage(emptyList(), false)
            }
        }

        val url = "$baseUrl/api/titles".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "50")

            if (authorId != null) {
                addQueryParameter("authors[]", authorId)
            }

            filters
                .filterIsInstance<UriFilter>()
                .forEach { it.addToUri(this) }
        }.build()

        return client.get(url).use { response ->
            parseMangaList(response)
        }
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) return null
        return fetchMangaDetails(getHid(url.pathSegments[1]))
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hid = getHid(manga.url)

        return if (fetchDetails && fetchChapters) {
            coroutineScope {
                val detailsDeferred = async { fetchMangaDetails(hid) }
                val chaptersDeferred = async { fetchChapters(manga) }
                SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
            }
        } else {
            val updatedManga = if (fetchDetails) fetchMangaDetails(hid) else manga
            val updatedChapters = if (fetchChapters) fetchChapters(manga) else chapters
            SMangaUpdate(updatedManga, updatedChapters)
        }
    }

    private suspend fun fetchMangaDetails(hid: String): SManga = client.get("$baseUrl/api/titles/$hid").parseAs<MangaDetailsResponse>().data.toSManga()

    private suspend fun fetchChapters(manga: SManga): List<SChapter> {
        var page = 1
        var lastPage: Int
        var displayVolumes = showAsVolumes
        val chapters = mutableListOf<SChapter>()

        val hid = getHid(manga.url)

        if (displayVolumes) {
            val url = "$baseUrl/api/titles/$hid/volumes"
            val volumes = client.get(url).parseAs<ApiResponse<VolumeDto>>().items
            if (volumes.isNotEmpty()) {
                val items = volumes.filter { it.language == langCode }
                    .forEach { chapters.add(it.toSChapter(manga.url)) }
            } else {
                displayVolumes = false // Fallback to chapter display
            }
        }

        if (!displayVolumes) {
            chapters.addAll(
                fetchAllChapters(hid).map { it.toSChapter(manga.url, langCode) },
            )
        }

        return chapters
            .let {
                if (!displayVolumes && mergeChapters) {
                    it.sortedBy { chapter ->
                        val isOfficial = chapter.scanlator!!.lowercase() == "official"
                        if (preferOfficial) !isOfficial else isOfficial
                    }.distinctBy { it.chapter_number }.sortedByDescending { it.chapter_number }
                } else {
                    it
                }
            }
    }

    private suspend fun fetchAllChapters(hid: String): List<ChapterDto> = coroutineScope {
        val firstUrl = "$baseUrl/api/titles/$hid/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("language", langCode)
            .addQueryParameter("sort", "number")
            .addQueryParameter("order", "desc")
            .addQueryParameter("page", "1")
            .addQueryParameter("limit", "200")
            .build()
        val firstData = client.get(firstUrl).parseAs<ApiResponse<ChapterDto>>()
        val lastPage = firstData.meta?.lastPage ?: 1

        val allItems = mutableListOf<ChapterDto>()
        allItems.addAll(firstData.items)

        if (lastPage > 1) {
            val deferred = (2..lastPage).map { page ->
                async {
                    val url = "$baseUrl/api/titles/$hid/chapters".toHttpUrl().newBuilder()
                        .addQueryParameter("language", langCode)
                        .addQueryParameter("sort", "number")
                        .addQueryParameter("order", "desc")
                        .addQueryParameter("page", page.toString())
                        .addQueryParameter("limit", "200")
                        .build()
                    client.get(url).parseAs<ApiResponse<ChapterDto>>().items
                }
            }
            deferred.awaitAll().forEach { allItems.addAll(it) }
        }

        allItems
    }

    override val supportRelatedMangasBySearch = true

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = (baseUrl + chapter.url).toHttpUrl().pathSegments
        val last = segments.last()

        val url = if (segments.contains("volume")) {
            "$baseUrl/api/volumes/$last"
        } else {
            val chapterId = last.substringBefore("-")
            "$baseUrl/api/chapters/$chapterId"
        }

        val data = client.get(url).parseAs<PagesResponse>()
        return data.data.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.url)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        ContentRatingFilter(contentRating),
        Filter.Separator(),
        TypeFilter(),
        Filter.Separator(),
        GenreModeFilter(),
        GenreFilter(),
        Filter.Separator(),
        ThemeFilter(),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        AuthorFilter(),
        YearFromFilter(),
        YearToFilter(),
        MinChapterFilter(),
        Filter.Separator(),
        SortFilter(),
    )

    // ============================= Utilities =============================

    private fun getHid(url: String): String {
        val lastPart = url.removeSuffix("/").substringAfterLast("/")
        return when {
            lastPart.contains(".") -> lastPart.substringAfterLast(".")
            lastPart.contains("-") -> lastPart.substringBefore("-")
            else -> lastPart
        }
    }

    // ========================= Preferences =========================

    private val contentRating: Set<String>
        get() = preferences.getStringSet(CONTENT_RATING_PREF, emptySet()) ?: emptySet()

    private val showAsVolumes: Boolean
        get() = preferences.getBoolean(PREF_SHOW_AS_VOLUMES, false)

    private val mergeChapters: Boolean
        get() = preferences.getBoolean(PREF_MERGE_CHAPTERS, false)

    private val preferOfficial: Boolean
        get() = preferences.getBoolean(PREF_PREFER_OFFICIAL, true)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = CONTENT_RATING_PREF
            title = "Content Rating (All by default)"
            entries = CONTENT_RATINGS.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            entryValues = CONTENT_RATINGS.toTypedArray()
            summary = contentRating.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
            setDefaultValue(emptySet<String>())
            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = selected.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
                true
            }
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_AS_VOLUMES
            title = "Prefer Volume Release"
            summary = SUMMARY_MSG
            setDefaultValue(false)
        }.also(screen::addPreference)

        val mergeChaptersPref = SwitchPreferenceCompat(screen.context).apply {
            key = PREF_MERGE_CHAPTERS
            title = "Merge duplicate chapters"
            summary = SUMMARY_MSG
            setDefaultValue(false)
        }.also { screen.addPreference(it) }

        val preferOfficialPref = SwitchPreferenceCompat(screen.context).apply {
            key = PREF_PREFER_OFFICIAL
            title = "Prefer official chapters"
            setDefaultValue(true)
            setEnabled(mergeChapters)
        }.also { screen.addPreference(it) }

        mergeChaptersPref.setOnPreferenceChangeListener { _, newValue ->
            preferOfficialPref.setEnabled(newValue as Boolean)
            true
        }
    }

    companion object {
        private const val CONTENT_RATING_PREF = "pref_content_rating"
        private const val PREF_SHOW_AS_VOLUMES = "show_as_volumes"
        private const val PREF_MERGE_CHAPTERS = "merge_chapters"
        private const val PREF_PREFER_OFFICIAL = "prefer_official"
        private const val SUMMARY_MSG = "Requires Chapter List Refresh to Apply"
    }
}
