package eu.kanade.tachiyomi.extension.pt.brscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ManhwaPageDto(
    val next: String? = null,
    val results: List<ManhwaDto>,
)

@Serializable
class ManhwaDto(
    private val id: Int,
    private val title: String,
    private val description: String? = null,
    @SerialName("original_title") private val originalTitle: String? = null,
    private val status: String? = null,
    private val thumbnail: ImagesDto? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$id"
        title = this@ManhwaDto.title
        description = MarkdownDescription.Builder()
            .description(this@ManhwaDto.description)
            .field("Título original", originalTitle?.takeUnless { it.equals(title, ignoreCase = true) })
            .build()
        status = when (this@ManhwaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = thumbnail?.original
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val title: String,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(mangaUrl: String, variant: Variant) = SChapter.create().apply {
        url = "$mangaUrl/chapter/$id?variant=${variant.key}"
        name = title
        scanlator = variant.label
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val images: ImagesDto,
)

@Serializable
class ImagesDto(
    val original: String? = null,
    private val translated: String? = null,
    private val raw: String? = null,
) {
    fun url(variant: Variant) = when (variant) {
        Variant.TRANSLATED -> translated
        Variant.ORIGINAL -> original
        Variant.RAW -> raw
    } ?: original
}

enum class Variant(val key: String, val label: String) {
    TRANSLATED("translated", "Português"),
    ORIGINAL("original", "Original"),
    RAW("raw", "Sem texto"),
}
