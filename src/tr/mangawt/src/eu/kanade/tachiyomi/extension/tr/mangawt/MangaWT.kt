package eu.kanade.tachiyomi.extension.tr.mangawt

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
import keiyoushi.utils.getLongOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaWT : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(page, sort = "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "popular"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart().orEmpty()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart().orEmpty()
        return mangaList(page, query.trim(), sort, status, type, genre)
    }

    private suspend fun mangaList(
        page: Int,
        query: String = "",
        sort: String,
        status: String = "",
        type: String = "",
        genre: String = "",
    ): MangasPage {
        // The genre endpoint is the only one that filters by genre, and it ignores the text query.
        val endpoint = if (genre.isNotBlank()) "$apiUrl/mangas/genre/$genre" else "$apiUrl/mangas"
        val url = endpoint.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank() && genre.isBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("sort", sort)
            if (status.isNotBlank()) {
                addQueryParameter("status", status)
            }
            if (type.isNotBlank()) {
                addQueryParameter("type", type)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
        }.build()

        val response = client.get(url).parseAs<MangaListResponse>()
        return MangasPage(response.mangas.map { it.toSManga() }, response.page < response.pages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "manga") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.memo.getString("slug")}/chapter/${chapter.memo.getString("number")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // Entries from the old Madara-based version store "/manga/<slug>/" as the url.
        val slug = manga.url.trim('/').substringAfterLast('/')
        val details = client.get("$apiUrl/mangas/slug/$slug").parseAs<MangaDto>()
        return SMangaUpdate(
            details.toSManga(),
            details.chapters
                .sortedByDescending { it.number }
                .map { it.toSChapter(details.id, details.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Locked chapters answer 403, which the app's Cloudflare interceptor turns into a misleading error.
        val lockedUntil = chapter.memo.getLongOrNull("lockedUntil")
        if (lockedUntil != null && lockedUntil > System.currentTimeMillis()) {
            throw Exception("Bölüm kilitli, henüz yayınlanmadı")
        }
        val url = "$apiUrl/mangas/${chapter.memo.getString("mangaId")}/chapters/${chapter.memo.getString("number")}/pages"
        return client.get(url).parseAs<PageListResponse>().pages
            .sortedBy { it.pageNumber }
            .mapIndexed { index, page ->
                Page(index, imageUrl = page.signedUrl)
            }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    companion object {
        private const val PAGE_SIZE = 20
    }
}
