package eu.kanade.tachiyomi.extension.all.kodokustudio

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SeriesDto(
    val slug: String,
    private val title: String,
    private val description: String? = null,
    private val coverUrl: String? = null,
) {
    fun toSManga(languageCode: String) = SManga.create().apply {
        url = "$slug/$languageCode"
        title = "${this@SeriesDto.title} [${languageCode.uppercase()}]"
        thumbnail_url = coverUrl
        description = this@SeriesDto.description
        status = SManga.ONGOING
    }
}

@Serializable
class ChapterEntryDto(
    val chapterNumber: String,
    val languageCode: String,
    val available: Boolean = false,
    private val uploadedAt: String? = null,
    private val availableAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$chapterNumber/$languageCode"
        name = "${if (available) "" else "🔒 "}Chapter $chapterNumber"
        chapter_number = chapterNumber.toFloatOrNull() ?: 0f
        date_upload = Instant.tryParse(availableAt ?: uploadedAt ?: "")
    }
}

@Serializable
class ChapterDetailDto(
    private val images: List<ImageDto> = emptyList(),
) {
    fun toPages() = images.sortedBy { it.sequence }
        .mapIndexed { index, image -> Page(index, imageUrl = image.url) }
}

@Serializable
class ImageDto(
    val sequence: Int = 0,
    val url: String,
)
