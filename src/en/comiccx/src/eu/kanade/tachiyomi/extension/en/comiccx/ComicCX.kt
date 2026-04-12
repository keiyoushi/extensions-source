package eu.kanade.tachiyomi.extension.en.comiccx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.dataimage.DataImageInterceptor
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("unused")
class ComicCX : HttpSource() {

    override val name = "Comic CX"
    override val baseUrl = "https://comic.cx"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    // =============================== Popular ================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popularity")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListResponse>()
        val mangas = data.manga.map { it.toSManga() }
        val hasNextPage = (data.pagination?.page ?: 1) < (data.pagination?.pages ?: 1)
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest =================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "latest")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details =================================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaItem>()
        .toSManga()
        .apply { initialized = true }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    // ============================== Chapters ================================

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaSlug = response.request.url.pathSegments[2]
        return response.parseAs<List<ChapterItem>>()
            .sortedByDescending { it.chapterNumber }
            .map { it.toSChapter(mangaSlug) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.url.substringBefore("/")
        val chapNumStr = if (chapter.chapter_number % 1 == 0f) {
            chapter.chapter_number.toInt().toString()
        } else {
            chapter.chapter_number.toString()
        }
        return "$baseUrl/manga/$slug/reader/$chapNumStr"
    }

    // =============================== Pages ==================================

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.substringBefore("/")
        val id = chapter.url.substringAfter("/")

        val url = "$apiUrl/manga/$slug/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", id)
            .build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.queryParameter("chapter_id")?.toIntOrNull()
        val chapters = response.parseAs<List<ChapterItem>>()

        val chapter = chapters.find { it.id == chapterId }
            ?: throw Exception("Chapter not found")

        return chapter.pages.mapIndexed { index, url ->
            Page(index, imageUrl = url.resolveImageUrl())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ================================

    private fun String?.resolveImageUrl(): String {
        if (this.isNullOrBlank()) return ""
        return when {
            startsWith("/") -> "$baseUrl$this"
            else -> this
        }
    }

    private fun String?.resolveCoverUrl(): String? {
        if (this.isNullOrBlank()) return null
        return when {
            startsWith("data:") -> "https://127.0.0.1/?" + this.substringAfter(":")
            startsWith("/") -> "$baseUrl$this"
            else -> this
        }
    }

    private fun MangaItem.toSManga() = SManga.create().apply {
        url = slug
        title = this@toSManga.title
        thumbnail_url = coverImage.resolveCoverUrl()
        author = this@toSManga.author
        artist = this@toSManga.artist
        genre = genres?.joinToString(", ")
        status = when (this@toSManga.status?.lowercase(Locale.ENGLISH)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            if (!this@toSManga.description.isNullOrBlank()) {
                append(this@toSManga.description)
            }
            val effectiveTier = this@toSManga.requiredTier ?: this@toSManga.tier
            if (!effectiveTier.isNullOrBlank() && effectiveTier != "free" && effectiveTier != "tier_0") {
                if (isNotEmpty()) append("\n\n")
                append("⚠ This title requires ${effectiveTier.replace("_", " ").uppercase()} access. Log in via WebView to read.")
            }
        }
    }

    private fun ChapterItem.toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$id"
        val chapNumStr = if (chapterNumber % 1 == 0f) chapterNumber.toInt().toString() else chapterNumber.toString()
        name = buildString {
            append("Chapter $chapNumStr")
            if (!title.isNullOrBlank()) append(" - $title")
        }
        chapter_number = chapterNumber
        date_upload = createdAt?.let { dateFormat.tryParse(it) } ?: 0L
    }

    @Suppress("SpellCheckingInspection")
    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    }

    // ================================ DTOs ==================================

    @Serializable
    data class MangaListResponse(
        val manga: List<MangaItem> = emptyList(),
        val pagination: Pagination? = null,
    )

    @Serializable
    data class Pagination(
        val page: Int,
        val limit: Int,
        val total: Int,
        val pages: Int,
    )

    @Serializable
    data class MangaItem(
        val id: Int,
        val title: String,
        val description: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val status: String? = null,
        @SerialName("cover_image") val coverImage: String? = null,
        val genres: List<String>? = null,
        val slug: String,
        @SerialName("required_tier") val requiredTier: String? = null,
        val tier: String? = null,
    )

    @Serializable
    data class ChapterItem(
        val id: Int,
        @SerialName("chapter_number") val chapterNumber: Float,
        val title: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        val pages: List<String> = emptyList(),
    )
}
