package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class BookDto(
    val id: String,
    val name: String,
    val source: String,
    val metadata: MetadataDto,
    val booksMetadata: BooksMetadataDto,
) {
    @Serializable
    class MetadataDto(
        val genres: List<String>,
        val status: String,
        val summary: String,
    )

    @Serializable
    class BooksMetadataDto(
        val authors: List<AuthorDto>,
    ) {
        @Serializable
        class AuthorDto(
            val name: String,
            val role: String,
        )
    }

    fun toSManga(domain: String): SManga = SManga.create().apply {
        title = name
        url = "/series/$id"
        description = buildString {
            append(metadata.summary)
            append("\n\n")
            append("Source: ")
            append(source)
        }
        thumbnail_url = "https://api.$domain/api/v1/series/$id/thumbnail"
        author = getRoles(listOf("writer"))
        artist = getRoles(listOf("inker", "colorist", "penciller"))
        genre = metadata.genres.joinToString()
        status = metadata.status.toStatus()
    }

    private fun String.toStatus(): Int {
        return when (this) {
            "ONGOING" -> SManga.ONGOING
            "ENDED" -> SManga.COMPLETED
            else -> SManga.COMPLETED
        }
    }

    private fun getRoles(roles: List<String>): String {
        return booksMetadata.authors
            .filter { roles.contains(it.role) }
            .joinToString { it.name }
    }
}

@Serializable
class ChapterDto(
    val id: String,
    val metadata: MetadataDto,
) {
    @Serializable
    class MetadataDto(
        val releaseDate: String? = null,
        val title: String,
    )

    fun toSChapter(seriesId: String): SChapter = SChapter.create().apply {
        url = "$seriesId;$id"
        name = metadata.title
        date_upload = dateFormat.tryParse(metadata.releaseDate)
    }

    companion object {
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        }
    }
}

@Serializable
class ChallengeDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("page_count")
    val pageCount: Int,
)

@Serializable
class PaginationDto(
    val hasNext: Boolean,
)
