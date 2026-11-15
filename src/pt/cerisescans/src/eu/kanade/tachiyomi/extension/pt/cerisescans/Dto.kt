package eu.kanade.tachiyomi.extension.pt.cerisescans

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class ComicListDto(
    private val data: List<ComicDto>,
    private val totalPages: Int,
) {
    fun toMangasPage(page: Int, baseUrl: String) = MangasPage(data.map { it.toSManga(baseUrl) }, hasNextPage = page < totalPages)
}

@Serializable
class ComicDto(
    private val slug: String,
    private val title: String,
    private val coverImage: String?,
    private val author: String?,
    private val artist: String?,
    private val description: String?,
    private val genres: List<String>?,
    private val status: String?,
    val lastChapters: List<ChapterDto>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@ComicDto.title
        thumbnail_url = coverImage?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        author = this@ComicDto.author
        artist = this@ComicDto.artist
        description = this@ComicDto.description
        genre = genres?.joinToString()
        status = when (this@ComicDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: String,
    private val title: String,
    private val createdAt: String?,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id
        name = title.ifBlank { "Capítulo ${number.removeSuffix(".0")}" }
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(createdAt)
    }
}
