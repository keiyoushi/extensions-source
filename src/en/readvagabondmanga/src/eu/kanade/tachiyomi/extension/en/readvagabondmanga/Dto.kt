package eu.kanade.tachiyomi.extension.en.readvagabondmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class MangaStatus {
    @SerialName("ongoing")
    ONGOING,

    @SerialName("completed")
    COMPLETED,

    @SerialName("hiatus")
    HIATUS,
}

@Serializable
class ChapterDto(
    val number: Int,
    private val title: String,
    private val volume: Int?,
    private val mangaId: Int,
    private val releaseDate: String,
    val pageCount: Int,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = title
        chapter_number = number.toFloat()
        url = "/volume-$volume/chapter-$number/#$mangaId"
        date_upload = Instant.tryParse(releaseDate)
        scanlator = "Read Vagabond Manga"
    }
}

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    private val author: String,
    private val artist: String,
    private val description: String,
    private val status: MangaStatus = MangaStatus.ONGOING,
    private val cover: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        url = "/#$id"
        thumbnail_url = cover
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        description = this@MangaDto.description
        status = when (this@MangaDto.status) {
            MangaStatus.ONGOING -> SManga.ONGOING
            MangaStatus.COMPLETED -> SManga.COMPLETED
            MangaStatus.HIATUS -> SManga.ON_HIATUS
        }
    }
}
