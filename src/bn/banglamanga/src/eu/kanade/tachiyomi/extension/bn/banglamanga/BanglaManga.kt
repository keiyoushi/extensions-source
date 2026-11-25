package eu.kanade.tachiyomi.extension.bn.banglamanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import kotlin.time.Instant

@Source
abstract class BanglaManga : KeiSource() {

    private val apiKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndnZXlkemZtdGVoZGtvc3h3bmFpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIzMzQyMDcsImV4cCI6MjEwNzkxMDIwN30.o7MQCNzwmVKUv_L9sGgsueLl68bpRdjeaHmZT2Gxn18"

    private val apiHeaders
        get() = headers.newBuilder()
            .set("apikey", apiKey)
            .set("Authorization", "Bearer $apiKey")
            .build()

    private suspend fun apiGet(url: HttpUrl): Response = client.get(url, apiHeaders, Fetch.XHR)

    private val apiBaseUrl =
        "https://wgeydzfmtehdkosxwnai.supabase.co/rest/v1/"

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val q = query.replace(Regex("[,()*]"), " ").trim()
        if (q.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val url = (apiBaseUrl + "mangas").toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,slug,title_bn,author,artist,status,synopsis_bn,cover_url,genres",
            )
            .addQueryParameter(
                "or",
                "(title_bn.ilike.*$q*,title_en.ilike.*$q*)",
            )
            .build()

        val results = apiGet(url)
            .parseAs<List<MangaDto>>()

        return MangasPage(results.map { it.toSManga() }, false)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchPage(page, "views.desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchPage(page, "updated_at.desc")

    private suspend fun fetchPage(page: Int, order: String): MangasPage {
        val pageSize = 20
        val offset = (page - 1) * pageSize

        val url = (apiBaseUrl + "mangas").toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,slug,title_bn,author,artist,status,synopsis_bn,cover_url,genres",
            )
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("order", order)
            .build()

        val results = apiGet(url)
            .parseAs<List<MangaDto>>()

        return MangasPage(
            results.map { it.toSManga() },
            results.size == pageSize,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = (apiBaseUrl + "mangas").toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,slug,title_bn,author,artist,status,synopsis_bn,cover_url,genres,chapters(id,number,title_bn,publish_at)",
            )
            .addQueryParameter("slug", "eq.${manga.url}")
            .build()

        val result = apiGet(url)
            .parseAs<List<MangaDto>>()
            .firstOrNull()
            ?: throw Exception("Manga not found")

        val chapterList = result.chapters.orEmpty()
            .sortedByDescending { it.number }
            .map { chapter ->
                SChapter.create().apply {
                    this.url = chapter.id
                    name = chapter.titleBn
                    chapter_number = chapter.number.toFloat()
                    date_upload = Instant.tryParse(chapter.publishAt)
                }
            }

        return SMangaUpdate(
            manga = result.toSManga(),
            chapters = chapterList,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (apiBaseUrl + "chapters").toHttpUrl().newBuilder()
            .addQueryParameter("select", "pages")
            .addQueryParameter("id", "eq.${chapter.url}")
            .build()

        val result = apiGet(url)
            .parseAs<List<ChapterPagesDto>>()
            .firstOrNull()

        return result?.pages.orEmpty().mapIndexed { index, page ->
            Page(index, imageUrl = page.url)
        }
    }

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        title = this@toSManga.titleBn
        url = this@toSManga.slug
        thumbnail_url = this@toSManga.coverUrl
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = this@toSManga.synopsisBn
        genre = this@toSManga.genres?.joinToString(", ")
        status = this@toSManga.status.toSMangaStatus()
    }

    private fun String?.toSMangaStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed", "complete" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled", "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}
