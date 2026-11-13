package eu.kanade.tachiyomi.extension.all.mangasincensura

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

@Serializable
class SeriesDto(
    val id: String,
    private val title: String,
    private val quote: String? = null,
    private val description: String? = null,
    private val rating: Double? = null,
    private val category: String? = null,
    private val subtitle: String? = null,
    private val covers: List<String> = emptyList(),
    private val totalChapters: Int,
    private val publishedLimit: Int? = null,
    private val freeLimit: Int? = null,
    private val isCompleted: Boolean = false,
    private val hasChapterZero: Boolean = false,
    private val storageSlug: String? = null,
) {
    val storageName get() = storageSlug ?: id

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/manhwa_index?id=$id"
        title = this@SeriesDto.title
        thumbnail_url = covers.firstOrNull()?.let { baseUrl.resolve(it)?.toString() }
        genre = (listOfNotNull(category) + subtitle?.split("•").orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .rating(rating)
            .description(quote)
            .description(this@SeriesDto.description)
            .build()
        status = if (isCompleted) SManga.COMPLETED else SManga.ONGOING
    }

    // Chapters past the published limit are announced but not out yet
    fun toChapters(label: String) = ((publishedLimit ?: totalChapters) downTo (if (hasChapterZero) 0 else 1)).map { number ->
        SChapter.create().apply {
            url = "/chapter?manhwa=$id&cap=$number"
            name = if (isVip(number)) "🔒 $label $number" else "$label $number"
            chapter_number = number.toFloat()
        }
    }

    fun isVip(chapter: Int) = freeLimit != null && chapter > freeLimit
}

@Serializable
class ManifestDto(val pages: List<ManifestPageDto>)

@Serializable
class ManifestPageDto(
    private val page: JsonPrimitive,
    private val file: String? = null,
) {
    // Older manifests name the file in "page", newer ones number the page and name the file in "file"
    val fileName get() = file ?: page.content
}
