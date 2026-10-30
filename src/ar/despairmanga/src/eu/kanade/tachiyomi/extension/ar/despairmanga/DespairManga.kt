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
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class DespairManga : KeiSource() {

    private val apiUrl get() = "$baseUrl/supabase-api/rest/v1"

    private val apiHeaders: Headers
        get() = headers.newBuilder()
            .add("apikey", ANON_KEY)
            .add("Authorization", "Bearer $ANON_KEY")
            .build()

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
    private val SManga.slug get() = baseUrl.toHttpUrl().resolve(url)!!.pathSegments.last { it.isNotEmpty() }

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
    ): SMangaUpdate = coroutineScope {
        val slug = manga.slug
        val cachedId = manga.memo[WORK_ID]?.stringOrNull
        val work = async {
            if (fetchDetails || cachedId == null) getWork(slug) ?: throw Exception("Series not found") else null
        }
        val workId = cachedId ?: work.await()!!.id
        val chapterList = async { if (fetchChapters) getChapters(workId, slug) else chapters }
        SMangaUpdate(work.await()?.toSManga() ?: manga, chapterList.await())
    }

    private suspend fun getChapters(workId: String, slug: String): List<SChapter> {
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,chapter_number,title,is_free,created_at")
            .addQueryParameter("work_id", "eq.$workId")
            .addQueryParameter("order", "chapter_number.desc,id.desc")
            .build()
        return client.get(url, apiHeaders).parseAs<List<ChapterDto>>().map { it.toSChapter(slug) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // chapters saved by the old WordPress source have no chapter id
        val chapterId = chapter.memo[CHAPTER_ID]?.stringOrNull ?: throw Exception("Refresh chapter list")
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "pages")
            .addQueryParameter("id", "eq.$chapterId")
            .build()
        return client.get(url, apiHeaders).parseAs<List<PagesDto>>()
            .firstOrNull()?.pages.orEmpty()
            .mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    companion object {
        const val WORK_ID = "workId"
        const val CHAPTER_ID = "id"
        private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiwiaWF0IjoxNzkwMzczMDE4LCJleHAiOjE5NDgwNTMwMTh9.xYgAlC_9SuUCdzdff6wBYGWzYFmAI9apP_mk4DCPaVI"
        private const val WORK_COLUMNS = "id,slug,title_ar,title_en,cover_url,genres,synopsis,status,author_or_studio"
        private const val PAGE_SIZE = 24
    }
}
