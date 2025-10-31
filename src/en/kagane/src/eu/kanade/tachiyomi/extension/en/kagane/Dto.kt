package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SearchDto(
    val content: List<Book>,
    val last: Boolean,
) {
    fun hasNextPage() = !last

    @Serializable
    class Book(
        val name: String,
        val id: String,
    ) {

        fun toSManga(domain: String): SManga = SManga.create().apply {
            title = name
            url = id
            thumbnail_url = "$domain/api/v1/series/$id/thumbnail"
        }
    }
}

@Serializable
class DetailsDto(
    val source: String,
    val authors: List<String>,
    val status: String,
    val summary: String?,
    val genres: List<String>,
    @SerialName("alternate_titles")
    val alternateTitles: List<AlternateTitles>,
) {
    @Serializable
    class AlternateTitles(
        val title: String,
    )

    fun toSManga(): SManga = SManga.create().apply {
        val desc = StringBuilder()
        if (!summary.isNullOrBlank()) desc.append(summary + "\n\n")
        desc.append("Source: ").append(source + "\n\n")

        if (alternateTitles.isNotEmpty()) {
            desc.append("Associated Name(s):\n\n")
            alternateTitles.forEach { desc.append("• ${it.title}\n") }
        }

        author = authors.joinToString()
        description = desc.toString()
        genre = genres.joinToString()
        status = this@DetailsDto.status.toStatus()
    }

    private fun String.toStatus(): Int {
        return when (this) {
            "ONGOING" -> SManga.ONGOING
            "ENDED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    val content: List<Book>,
) {
    @Serializable
    class Book(
        val id: String,
        @SerialName("series_id")
        val seriesId: String,
        val title: String,
        @SerialName("release_date")
        val releaseDate: String?,
        @SerialName("pages_count")
        val pagesCount: Int,
        @SerialName("number_sort")
        val number: Float,
    ) {
        fun toSChapter(): SChapter = SChapter.create().apply {
            url = "$seriesId;$id;$pagesCount"
            name = title
            date_upload = dateFormat.tryParse(releaseDate)
            chapter_number = number
        }
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }
}

@Serializable
class ChallengeDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("cache_url")
    val cacheUrl: String,
)
