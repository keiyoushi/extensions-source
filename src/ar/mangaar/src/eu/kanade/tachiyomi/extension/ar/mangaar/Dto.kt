package eu.kanade.tachiyomi.extension.ar.mangaar

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val manga: List<MangaDto>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
class TrendingDto(
    val popular: List<MangaDto>,
    val trending: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        thumbnail_url = coverUrl?.let { baseUrl.resolve(it)?.toString() }
    }
}

@Serializable
class RecentChaptersDto(val chapters: List<RecentChapterDto>)

@Serializable
class RecentChapterDto(
    @SerialName("manga_id") private val mangaId: Int,
    @SerialName("manga_title") private val mangaTitle: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = mangaId.toString()
        title = mangaTitle
        thumbnail_url = coverUrl?.let { baseUrl.resolve(it)?.toString() }
    }
}

@Serializable
class MangaDetailsDto(
    private val id: Int,
    private val title: String,
    @SerialName("title_en") private val titleEn: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("description_ar") private val description: String? = null,
    private val type: String? = null,
    private val genre: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val year: Int? = null,
    @SerialName("status_ar") private val status: String? = null,
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = id.toString()
        title = this@MangaDetailsDto.title
        thumbnail_url = coverUrl?.let { baseUrl.resolve(it)?.toString() }
        author = this@MangaDetailsDto.author?.takeIf { it.isNotBlank() }
        artist = this@MangaDetailsDto.artist?.takeIf { it.isNotBlank() }
        genre = (listOfNotNull(TYPE_LABELS[type]) + genre.orEmpty().split(',').map { it.trim() })
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when (this@MangaDetailsDto.status) {
            "مستمر" -> SManga.ONGOING
            "مكتمل" -> SManga.COMPLETED
            "متوقف" -> SManga.ON_HIATUS
            "ملغي" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .info(year?.toString())
            .description(this@MangaDetailsDto.description)
            .altTitles(listOfNotNull(titleEn))
            .build()
    }
}

@Serializable
class ChapterListDto(private val chapters: List<ChapterDto>) {
    fun toSChapterList(mangaId: String) = chapters.sortedByDescending { it.episodeNumber }.map { it.toSChapter(mangaId) }
}

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("episode_number") val episodeNumber: Float,
    @SerialName("mangadex_chapter_id") private val mangadexChapterId: String? = null,
    private val title: String? = null,
    @SerialName("publish_date") private val publishDate: String? = null,
    @SerialName("badge_type") private val badgeType: String? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        val number = episodeNumber.toString().removeSuffix(".0")
        val subtitle = title?.takeUnless { it.isBlank() || it == "الفصل $number" }
        url = id.toString()
        name = if (subtitle == null) "الفصل $number" else "الفصل $number - $subtitle"
        chapter_number = episodeNumber
        date_upload = Instant.tryParse(publishDate)
        // The site's own Arabic translations, Arabic chapters from MangaDex, and English ones from MangaDex
        scanlator = when (badgeType) {
            "translated" -> "مترجم"
            "arabic" -> "عربي"
            else -> "إنجليزي"
        }
        memo = buildJsonObject {
            put("manga", mangaId)
            put("number", number)
            if (badgeType != "translated") mangadexChapterId?.let { put("mangadex", it) }
        }
    }
}

@Serializable
class LocalPagesDto(private val pages: List<String>) {
    fun toPageList(baseUrl: HttpUrl) = pages.mapIndexed { index, path -> Page(index, imageUrl = baseUrl.resolve(path)!!.toString()) }
}

@Serializable
class ProxyPagesDto(private val pages: List<ProxyPageDto>) {
    fun toPageList(baseUrl: HttpUrl) = pages.mapIndexed { index, page -> Page(index, imageUrl = baseUrl.resolve(page.url)!!.toString()) }
}

@Serializable
class ProxyPageDto(val url: String)

@Serializable
class CategoriesDto(@SerialName("genre_options") private val genreOptions: List<GenreOptionDto>) {
    fun genres() = genreOptions.map { it.value }
}

@Serializable
class GenreOptionDto(val value: String)

private val TYPE_LABELS = mapOf(
    "manga" to "مانغا",
    "manhwa" to "مانهوا",
    "manhua" to "مانها",
)
