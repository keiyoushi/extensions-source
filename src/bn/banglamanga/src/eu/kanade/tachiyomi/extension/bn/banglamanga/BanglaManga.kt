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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
class BanglaManga(
    override val lang: String,
    override val id: Long,
) : KeiSource() {

    override val name = "Bangla Manga"
    override val baseUrl = "https://www.banglamanga.com"

    private val apiKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndnZXlkemZtdGVoZGtvc3h3bmFpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIzMzQyMDcsImV4cCI6MjEwNzkxMDIwN30.o7MQCNzwmVKUv_L9sGgsueLl68bpRdjeaHmZT2Gxn18"

    private val apiClient by lazy {
        client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

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

        val results = apiClient.get(url, Fetch.XHR)
            .parseAs<List<MangaDto>>()

        val mangaList = results.map { manga ->
            SManga.create().apply {
                title = manga.titleBn
                    ?: manga.titleEn
                    ?: "Unknown"

                this.url = "/${manga.id}"
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

        val results = apiClient.get(url, Fetch.XHR)
            .parseAs<List<MangaDto>>()

        val mangaList = results.map { manga ->
            SManga.create().apply {
                title = manga.titleBn ?: manga.titleEn ?: "Unknown"
                this.url = "/${manga.id}"
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
        val mangaId = manga.url.removePrefix("/")

        val url = (apiBaseUrl + "mangas").toHttpUrl().newBuilder()
            .addQueryParameter(
                "select",
                "id,slug,title_bn,title_en,author,artist,status,synopsis_bn,cover_url,genres,release_year,chapters(id,number,title_bn,status,volume,number_end,publish_at,views,reading_mode,credits)",
            )
            .addQueryParameter("id", "eq.$mangaId")
            .build()

        val result = apiClient.get(url, Fetch.XHR)
            .parseAs<List<MangaDetailsDto>>()
            .firstOrNull()
            ?: throw Exception("Manga not found")

        val updatedManga = SManga.create().apply {
            this.url = manga.url
            title = result.titleBn ?: result.titleEn ?: manga.title
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
            .sortedByDescending { it.number ?: 0.0 }
            .map { chapter ->
                SChapter.create().apply {
                    this.url = chapter.id.orEmpty()
                    name = chapter.titleBn
                        ?: "Chapter ${chapter.number ?: ""}"
                    chapter_number = chapter.number?.toFloat() ?: -1f
                    date_upload = runCatching {
                        java.time.Instant.parse(chapter.publishAt)
                            .toEpochMilli()
                    }.getOrDefault(0L)
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

        val result = apiClient.get(url, Fetch.XHR)
            .parseAs<List<ChapterPagesDto>>()
            .firstOrNull()

        return result?.pages.orEmpty().mapIndexed { index, page ->
            val imageUrl = page.url.orEmpty()
            Page(index, imageUrl, imageUrl)
        }
    }

    @Serializable
    private data class MangaDto(
        val id: String? = null,
        val slug: String? = null,
        @SerialName("title_bn")
        val titleBn: String? = null,
        @SerialName("title_en")
        val titleEn: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val status: String? = null,
        @SerialName("synopsis_bn")
        val synopsisBn: String? = null,
        @SerialName("cover_url")
        val coverUrl: String? = null,
        val genres: List<String>? = null,
        @SerialName("release_year")
        val releaseYear: Int? = null,
    )

    @Serializable
    private data class MangaDetailsDto(
        val id: String? = null,
        val slug: String? = null,
        @SerialName("title_bn")
        val titleBn: String? = null,
        @SerialName("title_en")
        val titleEn: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val status: String? = null,
        @SerialName("synopsis_bn")
        val synopsisBn: String? = null,
        @SerialName("cover_url")
        val coverUrl: String? = null,
        val genres: List<String>? = null,
        @SerialName("release_year")
        val releaseYear: Int? = null,
        val chapters: List<ChapterDto>? = null,
    )

    @Serializable
    private data class ChapterDto(
        val id: String? = null,
        val number: Double? = null,
        @SerialName("title_bn")
        val titleBn: String? = null,
        val status: String? = null,
        val volume: Int? = null,
        @SerialName("number_end")
        val numberEnd: Double? = null,
        @SerialName("publish_at")
        val publishAt: String? = null,
        val views: Int? = null,
        @SerialName("reading_mode")
        val readingMode: String? = null,
        val credits: kotlinx.serialization.json.JsonElement? = null,
    )

    @Serializable
    private data class ChapterPagesDto(
        val pages: List<ChapterPageDto>? = null,
    )

    @Serializable
    private data class ChapterPageDto(
        val url: String? = null,
        val name: String? = null,
        val w: Int? = null,
        val h: Int? = null,
    )
}
