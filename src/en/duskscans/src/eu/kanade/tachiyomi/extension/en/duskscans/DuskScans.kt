package eu.kanade.tachiyomi.extension.en.duskscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class DuskScans :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    private var catalog: Pair<Long, List<MangaDto>>? = null
    private val catalogMutex = Mutex()

    // returns the whole catalog and every listing is built from it client-side
    private suspend fun getCatalog(): List<MangaDto> = catalogMutex.withLock {
        val fresh = preferences.getBoolean(ALWAYS_FETCH_PREF, false)
        catalog?.takeIf { !fresh && System.currentTimeMillis() - it.first < CATALOG_TTL }?.second
            ?: client.get("$apiUrl/manga").parseAs<List<MangaDto>>().also {
                catalog = System.currentTimeMillis() to it
            }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = ALWAYS_FETCH_PREF
            title = "Always fetch latest catalog"
            summary = "Browsing and searching will be slower. Leave this off unless the catalog looks out of date."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = getCatalog().sortedByDescending { it.views }
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = getCatalog().sortedByDescending { it.latestUpdate }
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getCatalog()

        val status = filters.firstInstanceOrNull<StatusFilter>()?.checked.orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.checked.orEmpty()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val included = genreFilter?.included.orEmpty()
        val excluded = genreFilter?.excluded.orEmpty()

        val filtered = mangas.filter {
            (query.isBlank() || it.title.contains(query, ignoreCase = true)) &&
                (status.isEmpty() || it.status in status) &&
                (type.isEmpty() || it.type in type) &&
                it.genres.containsAll(included) &&
                it.genres.none { genre -> genre in excluded }
        }

        val sorted = when (filters.firstInstanceOrNull<SortFilter>()?.state ?: 0) {
            1 -> filtered.sortedByDescending { it.createdAt }
            2 -> filtered.sortedByDescending { it.views }
            3 -> filtered.sortedByDescending { it.rating }
            4 -> filtered.sortedBy { it.title }
            else -> filtered.sortedByDescending { it.latestUpdate }
        }

        return MangasPage(sorted.map { it.toSManga() }, false)
    }

    // ============================== Details ================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return getCatalog().firstOrNull { it.slug == slug }?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun getMangaDetails(manga: SManga): SManga = getCatalog().first { it.slug == manga.url }.toSManga()

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, number) = chapter.url.split("/")
        return "$baseUrl/series/$slug/chapter-$number"
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        // read the series page's RSC payload
        val rscHeaders = headers.newBuilder().add("RSC", "1").build()
        val chapters = client.get("$baseUrl/series/${manga.url}", rscHeaders)
            .parseAs<List<ChapterDto>> {
                it.substringAfter("\"initialChapters\":").substringBefore(",\"initialComments\"")
            }

        return chapters.map { chapter ->
            SChapter.create().apply {
                url = "${manga.url}/${chapter.number}/${chapter.id}"
                name = chapter.title.ifBlank { "Chapter ${chapter.number}" }
                chapter_number = chapter.number.toFloat()
                date_upload = chapter.releaseDate?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ============================== Pages ==================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/")
        val response = client.get("$apiUrl/chapter/$chapterId")
        return response.parseAs<ChapterDetailDto>().pageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ================================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = getCatalog().flatMap { it.genres }.distinct().sorted()
        return JsonArray(genres.map(::JsonPrimitive))
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(data?.parseAs<List<String>>().orEmpty()),
    )

    // ============================== Helpers ================================

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        url = slug
        title = this@toSManga.title
        thumbnail_url = cover
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = this@toSManga.description
        genre = genres.joinToString()
        status = when (this@toSManga.status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

private val CATALOG_TTL = 5.minutes.inWholeMilliseconds
private const val ALWAYS_FETCH_PREF = "always_fetch_catalog"
