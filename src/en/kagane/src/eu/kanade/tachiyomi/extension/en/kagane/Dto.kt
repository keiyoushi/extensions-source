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
    val data: Data,
) {
    @Serializable
    class Data(
        val metadata: Metadata,
        val source: String,
    ) {
        @Serializable
        class Metadata(
            val genres: List<String>,
            val status: String,
            val summary: String,
            val alternateTitles: List<Title>,
        ) {
            @Serializable
            class Title(
                val title: String,
            )
        }

        fun toSManga(): SManga = SManga.create().apply {
            val summary = StringBuilder()
            summary.append(metadata.summary)
                .append("\n\n")
                .append("Source: ")
                .append(source)

            if (metadata.alternateTitles.isNotEmpty()) {
                summary.append("\n\nAssociated Name(s):")
                metadata.alternateTitles.forEach { summary.append("\n").append("• ${it.title}") }
            }

            description = summary.toString()
            genre = metadata.genres.joinToString()
            status = metadata.status.toStatus()
        }

        private fun String.toStatus(): Int {
            return when (this) {
                "ONGOING" -> SManga.ONGOING
                else -> SManga.COMPLETED
            }
        }
    }
}

@Serializable
class ChapterDto(
    val data: Data,
) {
    @Serializable
    class Data(
        val content: List<Book>,
    ) {
        @Serializable
        class Book(
            val metadata: Metadata,
            val id: String,
            val seriesId: String,
            val created: String,
        ) {
            @Serializable
            class Metadata(
                val title: String,
            )

            fun toSChapter(): SChapter = SChapter.create().apply {
                url = "$seriesId;$id"
                name = metadata.title
                date_upload = dateFormat.tryParse(created)
            }
        }
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
)

@Serializable
class PagesCountDto(
    val data: Data,
) {
    @Serializable
    class Data(
        val media: PagesCount,
    ) {
        @Serializable
        class PagesCount(
            @SerialName("pagesCount")
            val pagesCount: Int,
        )
    }
}
