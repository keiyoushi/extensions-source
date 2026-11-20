package eu.kanade.tachiyomi.extension.bn.banglamanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class BanglaManga : KeiSource() {

    override val name = "Bangla Manga"
    override val baseUrl = "https://www.banglamanga.com"

    private val apiKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndnZXlkemZtdGVoZGtvc3h3bmFpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIzMzQyMDcsImV4cCI6MjEwNzkxMDIwN30.o7MQCNzwmVKUv_L9sGgsueLl68bpRdjeaHmZT2Gxn18"

    private val apiHeaders by lazy {
        headers.newBuilder()
            .set("apikey", apiKey)
            .set("Authorization", "Bearer $apiKey")
            .build()
    }

    private suspend fun apiGet(url: HttpUrl): okhttp3.Response = client.get(url, apiHeaders, Fetch.XHR)
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
                "id,slug,title_bn,title_en,author,artist,status,synopsis_bn,cover_url,genres,release_year",
            )
            .addQueryParameter(
                "or",
                "(title_bn.ilike.*$q*,title_en.ilike.*$q*)",
            )
            .build()

        val results = apiGet(url)
            .parseAs<List<MangaDto>>()

        val mangaList = results.map { manga ->
            SManga.create().apply {
                title = manga.titleBn

                this.url = manga.id
                thumbnail_url = manga.coverUrl.orEmpty()
                author = manga.author
                artist = manga.artist
                description = manga.synopsisBn
                genre = manga.genres?.joinToString(", ")

                status = when (manga.status?.lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "completed", "complete" -> SManga.COMPLETED
                    "hiatus" -> SManga.ON_HIATUS
                    "cancelled", "canceled" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }

        return MangasPage(mangaList, false)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchPage(page, "views.desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchPage(page, "updated_at.desc")

    private suspend fun fetchPage(page: Int, order: String): MangasPage {
        val pageSize = 20
        val offset = (page - 1) * pageSize

        val url = (apiBaseUrl + "mangas").toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,slug,title_bn,title_en,author,artist,status,synopsis_bn,cover_url,genres,release_year",
            )
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("order", order)
            .build()

        val results = apiGet(url)
            .parseAs<List<MangaDto>>()

        val mangaList = results.map { manga ->
            SManga.create().apply {
                title = manga.titleBn
                this.url = manga.id
                thumbnail_url = manga.coverUrl.orEmpty()
                author = manga.author
                artist = manga.artist
                description = manga.synopsisBn
                genre = manga.genres?.joinToString(", ")

                status = when (manga.status?.lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "completed", "complete" -> SManga.COMPLETED
                    "hiatus" -> SManga.ON_HIATUS
                    "cancelled", "canceled" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }

        return MangasPage(mangaList, results.size == pageSize)
    }
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaId = manga.url

        val url = (apiBaseUrl + "mangas").toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,slug,title_bn,title_en,author,artist,status,synopsis_bn,cover_url,genres,release_year,chapters(id,number,title_bn,status,volume,number_end,publish_at,views,reading_mode,credits)",
            )
            .addQueryParameter("id", "eq.$mangaId")
            .build()

        val result = apiGet(url)
            .parseAs<List<MangaDetailsDto>>()
            .firstOrNull()
            ?: throw Exception("Manga not found")

        val updatedManga = SManga.create().apply {
            this.url = manga.url
            title = result.titleBn
            thumbnail_url = result.coverUrl.orEmpty()
            author = result.author
            artist = result.artist
            description = result.synopsisBn
            genre = result.genres?.joinToString()
            status = when (result.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed", "complete" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled", "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

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
            manga = updatedManga,
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
            Page(index, page.url, page.url)
        }
    }
}
