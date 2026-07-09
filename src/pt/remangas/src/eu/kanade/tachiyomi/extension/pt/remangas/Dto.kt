package eu.kanade.tachiyomi.extension.pt.remangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class PageableDto<T>(
    @JsonNames("chapters", "comics")
    val list: List<T>,
    private val page: Long,
    @SerialName("total_pages")
    private val totalPages: Long = 0,
) {
    fun hasNextPage() = page < totalPages
}

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover
        url = "/manga/$slug"
    }
}

@Serializable
class ChapterDto(
    val number: Float,
    val slug: String,
    @SerialName("created_at")
    val createdAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        name = "Capítulo ${number.toString().replace(".0", "")}"
        chapter_number = number
        url = "/ler/$mangaSlug/$slug"
        date_upload = dateFormat.tryParse(createdAt)
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
    }
}
