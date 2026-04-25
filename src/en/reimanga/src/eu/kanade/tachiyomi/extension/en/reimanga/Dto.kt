package eu.kanade.tachiyomi.extension.en.reimanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.math.roundToInt

@Serializable
class MangaList(
    @JsonNames("initialData")
    val data: List<Manga>,
    val pagination: Pagination,
) {
    @Serializable
    class Pagination(
        private val currentPage: Int,
        private val totalPages: Int,
    ) {
        fun hasNextPage() = currentPage < totalPages
    }
}

@Serializable
class Manga(
    private val id: Long,
    @SerialName("name_url")
    private val slug: String,
    private val title: String,
    @SerialName("cover_url")
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "$slug-$id"
        title = this@Manga.title
        thumbnail_url = cover ?: "https://$DOMAIN/covers/$id/thumbnail.png"
    }
}

@Serializable
class TagList(
    val genres: List<Tag>,
    val tags: List<Tag>,
)

@Serializable
class Tag(
    val name: String,
    val slug: String,
)

@Serializable
class MangaPage(
    val manga: MangaDetails,
) {
    @Serializable
    class MangaDetails(
        private val id: Long,
        @SerialName("name_url")
        private val slug: String,
        private val title: String,
        @SerialName("cover_url")
        private val cover: String? = null,
        private val description: String? = null,
        @SerialName("alt_title")
        private val altTitle: String? = null,
        private val completed: Int = 0,
        private val rating: Double = -1.0,
        @SerialName("is_adult")
        private val isAdult: Int = 0,
        private val genres: List<Tag> = emptyList(),
        private val tags: List<Tag> = emptyList(),
        private val authors: List<Name> = emptyList(),
    ) {
        fun toSManga() = SManga.create().apply {
            url = "$slug-$id"
            title = this@MangaDetails.title
            thumbnail_url = cover ?: "https://$DOMAIN/covers/$id/thumbnail.png"
            description = buildString {
                if (rating > 0.0) {
                    val filled = (rating / 2).roundToInt().coerceIn(0, 5)
                    append("★".repeat(filled) + "☆".repeat(5 - filled))
                    append(" ")
                    append(rating)
                    append("\n\n")
                }
                this@MangaDetails.description?.takeIf { it.isNotBlank() }?.also {
                    append(it.trim())
                    append("\n\n")
                }
                altTitle?.takeIf { it.isNotBlank() }?.also {
                    append("Alternative Titles:\n")
                    it.split(",", ";").forEach { title ->
                        append("- ")
                        append(title.trim())
                        append("\n")
                    }
                    append("\n")
                }
            }
            status = if (completed == 1) SManga.COMPLETED else SManga.ONGOING
            author = authors.joinToString { it.name.trim().removeSuffix(",").trim() }
            genre = buildList {
                if (isAdult == 1) {
                    add("Adult")
                }
                genres.mapTo(this) { it.name.trim() }
                tags.mapTo(this) { it.name.trim() }
            }.joinToString()
        }

        @Serializable
        class Name(
            val name: String,
        )
    }
}

@Serializable
class ChapterList(
    val manga: Manga,
    val chapters: List<Chapter>,
) {
    @Serializable
    class Manga(
        val id: Long,
        @SerialName("name_url")
        val slug: String,
    )

    @Serializable
    class Chapter(
        val id: Long,
        val name: String,
        @SerialName("gdrive_upload_date")
        val uploadDate: String? = null,
        @SerialName("updated_at")
        val updatedAt: String? = null,
        @SerialName("created_at")
        val createdAt: String? = null,
    )
}

@Serializable
class Images(
    val images: List<Image>,
) {
    @Serializable
    class Image(
        @SerialName("image_url")
        val url: String,
    )
}
