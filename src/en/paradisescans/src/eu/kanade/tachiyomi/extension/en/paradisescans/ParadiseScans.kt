package eu.kanade.tachiyomi.extension.en.paradisescans

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import kotlin.time.Instant

@Source
abstract class ParadiseScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    private val showLockedChapters: Boolean
        get() = preferences.getBoolean(SHOW_LOCKED_CHAPTERS_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTERS_PREF
            title = "Show locked chapters"
            summaryOn = "Locked chapters will be shown in the chapter list"
            summaryOff = "Locked chapters will be hidden from the chapter list"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(
        client.get(
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/series")
                .addQueryParameter("sort", "sales")
                .addQueryParameter("dir", "desc")
                .addQueryParameter("per_page", "12")
                .addQueryParameter("page", page.toString())
                .build(),
        ),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(
        client.get(
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/series")
                .addQueryParameter("sort", "latest_chapter")
                .addQueryParameter("per_page", "12")
                .addQueryParameter("page", page.toString())
                .build(),
        ),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = parseMangasPage(
        client.get(
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/series")
                .addQueryParameter("per_page", "12")
                .addQueryParameter("page", page.toString())
                .apply {
                    if (query.isNotBlank()) addQueryParameter("q", query.trim())
                    filters.applyToUrl(this)
                }
                .build(),
        ),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1)?.takeIf { url.pathSegments.getOrNull(0) == "series" } ?: return null
        return getMangaDetails(SManga.create().apply { this.url = "/series/$slug" })
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val result = response.parseAs<ParadiseApiResponse<SeriesDto>>()
        val mangas = result.data.map { series ->
            SManga.create().apply {
                url = "/series/${series.slug}"
                title = series.title
                thumbnail_url = series.coverUrl?.let { toAbsoluteUrl(it) }
            }
        }
        val hasNextPage = (result.meta?.currentPage ?: 1) < (result.meta?.lastPage ?: 1)

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga) else manga
        val updatedChapters = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val slug = manga.url.removePrefix("/series/").trimEnd('/')
        val response = client.get("$baseUrl/api/series/$slug")
        val series = response.parseAs<SeriesDto>()

        return manga.apply {
            title = series.title
            thumbnail_url = series.coverUrl?.let { toAbsoluteUrl(it) }
            description = series.description
            author = series.author
            artist = series.artist ?: series.author
            genre = series.genres.joinToString()
            status = when (series.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.removePrefix("/series/").trimEnd('/')
        val response = client.get("$baseUrl/api/series/$slug/chapters")
        val chapters = response.parseAs<List<ChapterDto>>()

        return chapters
            .filter { chapter ->
                val isLocked = (chapter.price != null && chapter.price > 0) ||
                    chapter.isPremium ||
                    chapter.earlyAccess ||
                    chapter.earlyLocked
                showLockedChapters || !isLocked
            }
            .sortedByDescending { it.number ?: 0.0 }
            .map { chapter ->
                SChapter.create().apply {
                    val number = chapter.number?.let(::formatNumber)
                    // keep the site's reader URL so existing library entries stay valid
                    url = number?.let { "/series/$slug/chapter/$it" } ?: chapter.id
                    memo = buildJsonObject { put("id", chapter.id) }
                    name = number?.let {
                        when {
                            chapter.title.isNullOrBlank() || chapter.title == it -> "Chapter $it"
                            chapter.title.startsWith("Chapter", ignoreCase = true) -> chapter.title
                            else -> "Chapter $it - ${chapter.title}"
                        }
                    } ?: chapter.title.orEmpty().ifBlank { "Chapter" }
                    chapter.number?.toFloat()?.let { chapter_number = it }
                    date_upload = chapter.createdAt?.let { Instant.tryParse(it) } ?: 0L
                }
            }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.memo["id"]?.string ?: throw Exception("Refresh Chapter List")
        val response = client.get("$baseUrl/api/chapters/$chapterId/pages")
        val pages = response.parseAs<List<PageDto>>()

        return pages
            .sortedBy { it.pageNumber }
            .mapIndexed { index, page ->
                Page(index, imageUrl = toAbsoluteUrl(page.imageUrl))
            }
    }

    private fun formatNumber(number: Double): String = if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

    override fun getFilterList(data: kotlinx.serialization.json.JsonElement?): FilterList = getFilters()

    private fun toAbsoluteUrl(url: String): String = baseUrl.toHttpUrl().resolve(url)?.toString() ?: url
}

private const val SHOW_LOCKED_CHAPTERS_PREF = "pref_show_locked_chap"
