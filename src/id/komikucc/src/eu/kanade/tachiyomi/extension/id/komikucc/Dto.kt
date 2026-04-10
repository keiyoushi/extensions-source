package eu.kanade.tachiyomi.extension.id.komikucc

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class MangaList(
    val data: List<Manga>,
    @SerialName("current_page")
    private val currentPage: Int,
    @SerialName("last_page")
    private val lastPage: Int,
) {
    @Serializable
    class Manga(
        private val link: String,
        private val title: String,
        private val img: String,
    ) {
        fun toSManga() = SManga.create().apply {
            url = link
            title = this@Manga.title
            thumbnail_url = when {
                img.startsWith("http") -> img
                else -> CDN_URL + img.removePrefix("/")
            }
        }
    }

    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class GenreList(
    val genres: List<Genre>,
)

@Serializable
class Genre(
    val title: String,
    val link: String,
)

@Serializable
class ChaptersList(
    val chapters: List<Chapter>,
) {
    @Serializable
    class Chapter(
        private val link: String,
        private val title: String,
        @SerialName("created_at")
        private val createdAt: String? = null,
        @SerialName("updated_at")
        private val updatedAt: String? = null,
    ) {
        fun toSChapter() = SChapter.create().apply {
            url = link
            name = title
            date_upload = dateFormat.tryParse(updatedAt ?: createdAt)
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT)

@Serializable
class Images(
    val images: List<String>,
)
