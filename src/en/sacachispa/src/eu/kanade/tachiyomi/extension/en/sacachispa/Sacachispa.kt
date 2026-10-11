package eu.kanade.tachiyomi.extension.en.sacachispa

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class Sacachispa :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(page, "$API_URL/manga")

    // =============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ==============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchMangaList(page, "$API_URL/manga/search", query)

    private suspend fun fetchMangaList(page: Int, endpoint: String, query: String? = null): MangasPage {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                if (query != null) addQueryParameter("q", query)
            }
            .build()

        val response = client.get(url).parseAs<MangaListResponse>()

        return MangasPage(
            mangas = response.data.map { it.toSManga() },
            hasNextPage = response.pagination.page < response.pagination.pages,
        )
    }

    // =============================== Details =============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null

        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        return fetchMangaDetails(SManga.create().apply { this.url = id })
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val details = client.get("$API_URL/manga/${manga.url}").parseAs<MangaResponse>().data

        return SManga.create().apply {
            // manga.url is immutable for existing entries, so legacy slug-based manga can't be
            // migrated by rewriting it; keep the id in memo to avoid re-resolving on every refresh.
            url = manga.url
            memo = buildJsonObject {
                put("id", details.id)
                put("slug", details.slug)
            }
            title = details.title
            thumbnail_url = details.covers.firstOrNull()?.image?.toCoverUrl()
            author = details.authors.joinToString { it.name }.ifEmpty { null }
            artist = details.artists.joinToString { it.name }.ifEmpty { null }
            genre = details.genres.joinToString { it.name }.ifEmpty { null }
            description = details.synopses.firstOrNull()?.synopsis
            status = details.status.toStatus()
        }
    }

    // ============================== Chapters =============================

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        // The releases endpoint is keyed by the manga UUID; library entries created
        // before the site rewrite still hold the slug.
        val mangaId = manga.url.takeIf { UUID_REGEX.matches(it) }
            ?: manga.memo.getStringOrNull("id")
            ?: resolveMangaId(manga.url)

        val chapters = mutableListOf<SChapter>()
        var page = 1
        var lastPage: Int
        val patreon = hidePatreon()

        do {
            val response = client.get("$API_URL/releases?mangaId=$mangaId&page=$page&limit=$CHAPTER_PAGE_SIZE")
                .parseAs<ReleaseListResponse>()

            chapters += response.data.mapNotNull { it.toSChapter(patreon) }
            lastPage = response.pagination.pages
            page++
        } while (page <= lastPage)

        return chapters.sortedByDescending { it.chapter_number }
    }

    private suspend fun resolveMangaId(slug: String): String = client.get("$API_URL/manga/$slug").parseAs<MangaResponse>().data.id

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$API_URL/releases/${chapter.url}/pages", ensureSuccess = false)

        if (response.code == 403) {
            // Locked (Patreon-exclusive) chapters answer with 403 and a JSON error body.
            val message = runCatching { response.parseAs<ErrorResponse>().error?.message }.getOrNull()
            if (message != null) throw Exception(message)
        }
        if (!response.isSuccessful) throw HttpException(response.code)

        val items = response.parseAs<PageListResponse>().data.items

        return items.mapIndexed { index, item -> Page(index, imageUrl = item.url) }
    }

    // =============================== URLs ================================

    // The site's manga route needs both segments; the slug is only cosmetic, so the id works as a fallback.
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}/${manga.memo.getStringOrNull("slug") ?: manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    // ============================== Mapping ==============================

    private fun MangaListDto.toSManga() = SManga.create().apply {
        url = id
        memo = buildJsonObject { put("slug", slug) }
        title = this@toSManga.title
        thumbnail_url = cover?.toCoverUrl()
    }

    private fun ReleaseDto.toSChapter(patreon: Boolean): SChapter? {
        if (patreon && chapter.patreonOnly == true) return null
        val prefix = if (chapter.patreonOnly == true) "\uD83D\uDD12 " else ""
        return SChapter.create().apply {
            url = id
            name = "${prefix}Chapter ${chapter.chapter}" + chapter.title?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
            chapter_number = chapter.chapter.toFloatOrNull() ?: -1f
            date_upload = Instant.tryParse(publishedAt)
        }
    }

    private fun String.toCoverUrl() = if (startsWith("http")) this else "$CDN_URL/${trimStart('/')}"

    private fun String.toStatus() = when (this) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "DROPPED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Preferences ==============================
    private fun hidePatreon(): Boolean = preferences.getBoolean(HIDE_LOCKED_CHAPTERS, true)
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_CHAPTERS
            title = HIDE_LOCKED_CHAPTERS_TITLE
            summaryOn = HIDE_LOCKED_CHAPTERS_SUM_ON
            summaryOff = HIDE_LOCKED_CHAPTERS_SUM_OFF
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private companion object {
        const val API_URL = "https://api.sacachispa.site/api"
        const val CDN_URL = "https://cdn.sacachispa.site"
        const val HIDE_LOCKED_CHAPTERS = "hide_patreon_chapters"
        const val HIDE_LOCKED_CHAPTERS_TITLE = "Hide Patreon-exclusive chapters"
        const val HIDE_LOCKED_CHAPTERS_SUM_ON = "Chapters will be hidden"
        const val HIDE_LOCKED_CHAPTERS_SUM_OFF = "Chapters will be marked with an icon: \uD83D\uDD12"
        const val PAGE_SIZE = 24
        const val CHAPTER_PAGE_SIZE = 500
        private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
