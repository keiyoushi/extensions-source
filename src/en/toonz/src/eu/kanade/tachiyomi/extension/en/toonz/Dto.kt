package eu.kanade.tachiyomi.extension.en.toonz

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val chapterNumber: String,
    private val title: String? = null,
    private val slug: String,
    private val date: String? = null,
) {
    fun toSChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        val chNum = chapterNumber.toFloatOrNull() ?: -1f
        chapter_number = chNum
        val cleanNum = if (chNum >= 0) chNum.toString().removeSuffix(".0") else chapterNumber
        val chTitle = title?.takeIf { it.isNotBlank() }
        name = if (chTitle != null) {
            if (chTitle.startsWith("Chapter", ignoreCase = true) || chTitle.startsWith("Ch.", ignoreCase = true)) {
                chTitle
            } else {
                "Chapter $cleanNum: $chTitle"
            }
        } else {
            "Chapter $cleanNum"
        }
        val cleanSlug = if (slug.startsWith("chapter-")) slug.removePrefix("chapter-") else slug
        url = cleanSlug
        memo = buildJsonObject {
            put("mangaUrl", mangaUrl)
        }
        date_upload = Instant.tryParse(date)
    }
}

@Serializable
class ChapterImagesDto(
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val filename: String,
)

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)
