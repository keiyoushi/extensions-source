package eu.kanade.tachiyomi.extension.en.zinmanganet

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class ChapterListResponse(
    val data: ChapterData,
)

@Serializable
class ChapterData(
    val chapters: List<ChapterDto>,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter(mangaPath: String) = SChapter.create().apply {
        url = chapterSlug
        name = chapterName
        date_upload = Instant.tryParse(updatedAt)
        memo = buildJsonObject { put("mangaPath", mangaPath) }
    }
}
