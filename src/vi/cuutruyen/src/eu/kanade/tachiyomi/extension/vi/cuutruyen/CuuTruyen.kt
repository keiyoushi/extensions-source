package eu.kanade.tachiyomi.extension.vi.cuutruyen

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class CuuTruyen :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(ImageInterceptor())
        rateLimit(5)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = coverPreferenceKey
            title = "Chất lượng ảnh bìa"
            summary = "%s"
            entries = arrayOf("Chất lượng cao (Desktop)", "Chất lượng thấp (Mobile)")
            entryValues = arrayOf(desktopCover, mobileCover)
            setDefaultValue(desktopCover)
        }.also(screen::addPreference)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/v2/mangas/top".toHttpUrl().newBuilder()
            .addQueryParameter("duration", "all")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .build()
        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/v2/mangas/recently_updated".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", latestPageSize.toString())
            .build()
        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val tagQuery = filters.firstInstanceOrNull<TagFilter>()
            ?.selectedNames()
            .orEmpty()
            .joinToString(" AND ") { "\"$it\"" }
        val url = "$baseUrl/api/v2/mangas/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("tags", tagQuery)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .build()
        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "mangas") return null
        val mangaId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = mangaId.toString() }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.substringAfterLast('/')
        val updatedManga = async {
            if (fetchDetails) {
                client.get("$baseUrl/api/v2/mangas/$mangaId")
                    .parseAs<MangaDetailResponse>()
                    .data
                    .toSManga(useMobileCover)
            } else {
                manga
            }
        }
        val updatedChapters = async {
            if (fetchChapters) {
                client.get("$baseUrl/api/v2/mangas/$mangaId/chapters")
                    .parseAs<ChapterListResponse>()
                    .data
                    .map { it.toSChapter(mangaId) }
            } else {
                chapters
            }
        }
        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/mangas/${manga.url.substringAfterLast('/')}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/mangas/${chapter.memo["mangaId"]!!.string}/chapters/${chapter.url}"

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl/api/v2/chapters/${chapter.url}")
        .parseAs<ChapterReaderResponse>()
        .data
        .pages
        .sortedBy(ChapterPageDto::order)
        .mapIndexed { index, page ->
            Page(index, imageUrl = page.imageUrlWithDrm())
        }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/v2/tags/popular")
        .parseAs<TagResponse>()
        .data
        .allTags()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<TagOption>>())

    private fun MangaListResponse.toMangasPage(): MangasPage = MangasPage(
        mangas = data.map { it.toSManga(useMobileCover) },
        hasNextPage = metadata.currentPage < metadata.totalPages,
    )

    private val pageSize = 24
    private val latestPageSize = 30
    private val useMobileCover
        get() = preferences.getString(coverPreferenceKey, desktopCover) == mobileCover
    private val coverPreferenceKey = "preferred_cover"
    private val desktopCover = "cover_url"
    private val mobileCover = "cover_mobile_url"
}
