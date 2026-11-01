package eu.kanade.tachiyomi.extension.vi.tulinhtruyen

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
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class TuLinhTruyen :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("User-Agent", "MangaVaultExtension/1.4.8 (Android; OkHttp)")
        set("X-Manga-Token", API_TOKEN)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListPage(mangaListUrl(page).toString())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = mangaListUrl(page).newBuilder()
            .addQueryParameter("title", query)
            .build()
        return mangaListPage(url.toString())
    }

    private fun mangaListUrl(page: Int): HttpUrl = "$baseUrl/manga".toHttpUrl().newBuilder()
        .addQueryParameter("limit", PAGE_LIMIT.toString())
        .addQueryParameter("offset", ((page - 1) * PAGE_LIMIT).toString())
        .addQueryParameter("includes[]", "cover_art")
        .build()

    private suspend fun mangaListPage(url: String): MangasPage {
        val res = client.get(url, Fetch.XHR).use { it.parseAs<MangaListResponse>() }
        val manga = res.data.map { it.toSManga(baseUrl) }
        return MangasPage(manga, res.offset + res.limit < res.total)
    }

    // ============================= Manga URL ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url.substringAfterLast('/')}?view=web"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val mangaId = when (url.pathSegments.firstOrNull()) {
            "manga" -> url.pathSegments.getOrNull(1)
            "chapter" -> url.pathSegments.getOrNull(1)
                ?.let { chapterId ->
                    client.get("$baseUrl/chapter/$chapterId", Fetch.XHR).use {
                        it.parseAs<ChapterDetailResponse>()
                    }.data.relationships.firstOrNull { rel -> rel.type == "manga" }?.id
                }
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$mangaId" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.substringAfterLast('/')
        val detailsDeferred = async {
            if (fetchDetails) {
                val detailUrl = "$baseUrl/manga/$mangaId".toHttpUrl().newBuilder()
                    .addQueryParameter("includes[]", "cover_art")
                    .addQueryParameter("includes[]", "author")
                    .build()
                client.get(detailUrl, Fetch.XHR)
                    .use { it.parseAs<MangaDetailResponse>() }.data.toSManga(baseUrl, manga.url)
            } else {
                manga
            }
        }
        val chaptersDeferred = async {
            if (fetchChapters) {
                val feedUrl = "$baseUrl/manga/$mangaId/feed".toHttpUrl().newBuilder()
                    .addQueryParameter("limit", "500")
                    .addQueryParameter("order[chapter]", "desc")
                    .build()
                client.get(feedUrl, Fetch.XHR)
                    .use { it.parseAs<ChapterListResponse>() }.data.map { it.toSChapter() }
            } else {
                chapters
            }
        }
        SMangaUpdate(manga = detailsDeferred.await(), chapters = chaptersDeferred.await())
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('/')
        val res = client.get("$baseUrl/at-home/server/$chapterId", Fetch.XHR)
            .use { it.parseAs<AtHomeResponse>() }
        val base = res.baseUrl ?: "$baseUrl/media"
        val quality = preferences.getString(PREF_IMAGE_QUALITY, DEFAULT_IMAGE_QUALITY)
            ?: DEFAULT_IMAGE_QUALITY
        return res.chapter.data.mapIndexed { i, file ->
            Page(i, imageUrl = "$base/$quality/${res.chapter.hash}/$file")
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url.substringAfterLast('/')}?view=web"

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGE_QUALITY
            title = "Chất lượng ảnh chương"
            summary = "%s"
            entries = arrayOf(
                "Gốc WebP (Khuyên dùng - Nhanh & Nhẹ)",
                "Gốc nguyên bản (Original PNG/JPG)",
                "Tiết kiệm dữ liệu (1600px WebP)",
                "Tiết kiệm dữ liệu (1200px WebP)",
            )
            entryValues = arrayOf("s0-rw", "s0", "s1600-rw", "s1200-rw")
            setDefaultValue(DEFAULT_IMAGE_QUALITY)
        }.also(screen::addPreference)
    }

    companion object {
        private const val API_TOKEN = "tl_live_74b68abdc9db00654620cc3e0f0e2414667b0308a09bfc05"
        private const val PAGE_LIMIT = 20
        private const val PREF_IMAGE_QUALITY = "mangavault_image_quality"
        private const val DEFAULT_IMAGE_QUALITY = "s0"
    }
}
