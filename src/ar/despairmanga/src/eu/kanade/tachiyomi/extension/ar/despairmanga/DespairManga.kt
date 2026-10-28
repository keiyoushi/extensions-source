package eu.kanade.tachiyomi.extension.ar.despairmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class DespairManga : KeiSource() {

    private val apiUrl get() = "$baseUrl/supabase-api/rest/v1"

    private val apiHeaders by lazy {
        headers.newBuilder()
            .add("apikey", ANON_KEY)
            .add("Authorization", "Bearer $ANON_KEY")
            .build()
    }

    override suspend fun getPopularManga(page: Int) = getWorks(page, "views_count.desc")

    override suspend fun getLatestUpdates(page: Int) = getWorks(page, "updated_at.desc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val term = query.trim().replace("\\", "\\\\").replace("\"", "\\\"")
        return getWorks(page, "views_count.desc") {
            addQueryParameter("or", "(title_ar.ilike.\"*$term*\",title_en.ilike.\"*$term*\")")
        }
    }

    private suspend fun getWorks(
        page: Int,
        order: String,
        filter: HttpUrl.Builder.() -> Unit = {},
    ): MangasPage {
        val url = "$apiUrl/manga_works".toHttpUrl().newBuilder()
            .addQueryParameter("select", WORK_COLUMNS)
            .apply(filter)
            .addQueryParameter("order", order)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .build()
        val works = client.get(url, apiHeaders).parseAs<List<WorkDto>>()
        return MangasPage(works.map { it.toSManga() }, works.size == PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return getWork(slug)?.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.slug}"

    // entries saved by the old WordPress source still use /manga/{slug}/ as url
    private val SManga.slug get() = url.trimEnd('/').substringAfterLast('/')

    private suspend fun getWork(slug: String): WorkDto? {
        val url = "$apiUrl/manga_works".toHttpUrl().newBuilder()
            .addQueryParameter("select", WORK_COLUMNS)
            .addQueryParameter("slug", "eq.$slug")
            .addQueryParameter("limit", "1")
            .build()
        return client.get(url, apiHeaders).parseAs<List<WorkDto>>().firstOrNull()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug
        val work = getWork(slug) ?: throw Exception("Series not found")
        return SMangaUpdate(
            manga = work.toSManga(),
            chapters = if (fetchChapters) getChapters(work.id, slug) else chapters,
        )
    }

    private suspend fun getChapters(workId: String, slug: String): List<SChapter> {
        val result = mutableListOf<ChapterDto>()
        do {
            val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("select", "id,chapter_number,title,is_free,created_at")
                .addQueryParameter("work_id", "eq.$workId")
                .addQueryParameter("order", "chapter_number.desc,id.desc")
                .addQueryParameter("limit", CHAPTER_CHUNK.toString())
                .addQueryParameter("offset", result.size.toString())
                .build()
            val chunk = client.get(url, apiHeaders).parseAs<List<ChapterDto>>()
            result += chunk
        } while (chunk.size == CHAPTER_CHUNK)
        return result.map { it.toSChapter(slug) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "pages")
            .addQueryParameter("id", "eq.${chapter.memo.getString("id")}")
            .build()
        return client.get(url, apiHeaders).parseAs<List<PagesDto>>()
            .firstOrNull()?.pages.orEmpty()
            .mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    companion object {
        private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiwiaWF0IjoxNzkwMzczMDE4LCJleHAiOjE5NDgwNTMwMTh9.xYgAlC_9SuUCdzdff6wBYGWzYFmAI9apP_mk4DCPaVI"
        private const val WORK_COLUMNS = "id,slug,title_ar,title_en,cover_url,genres,synopsis,status,author_or_studio"
        private const val PAGE_SIZE = 24

        // supabase caps a single response at 1000 rows by default
        private const val CHAPTER_CHUNK = 1000
    }
}
