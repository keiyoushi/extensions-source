package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class HomeDto(
    val tops: List<MangaDto>,
    val trending: List<MangaDto>,
    val news: List<NewsDto>,
    val reading: List<NewsDto>,
    val categories: List<CategoryDto>,
    val tags: List<String> = emptyList(),
) {
    fun catalogMangas(): List<MangaDto> = buildList {
        addAll(tops)
        addAll(trending)
        addAll(news.map { it.manga })
        addAll(reading.map { it.manga })
        categories.forEach { addAll(it.list) }
    }.distinctBy { it.id }
}

@Serializable
internal class NewsDto(
    val manga: MangaDto,
)

@Serializable
internal class CategoryDto(
    val list: List<MangaDto>,
)

@Serializable
internal class MangaDto(
    val id: Long,
    val title: String,
    @SerialName("alternative_title") val alternativeTitle: String? = null,
    val description: String,
    val tags: List<String>,
    val thumbnail: String,
    val finished: Boolean? = null,
    val chapters: List<ChapterDto>? = null,
) {
    fun toSManga(details: Boolean = false): SManga = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        thumbnail_url = thumbnail.normalizeImageUrl()
        status = when (finished) {
            true -> SManga.COMPLETED
            false -> SManga.ONGOING
            null -> SManga.UNKNOWN
        }

        if (details) {
            description = buildString {
                alternativeTitle
                    ?.takeIf { it.isNotBlank() && !it.equals(this@MangaDto.title, ignoreCase = true) }
                    ?.let {
                        append("Título alternativo: ")
                        append(it)
                        append("\n\n")
                    }
                append(this@MangaDto.description)
            }
            genre = tags.joinToString()
            initialized = true
        }
    }
}

@Serializable
internal class ChapterDto(
    val id: Long,
    val title: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id.toString()
        name = title
        CHAPTER_NUMBER.find(title)?.value
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?.let { chapter_number = it }
    }
}

@Serializable
internal class ChapterPagesDto(
    val pages: List<String>,
) {
    fun toPageList(): List<Page> = pages.mapIndexed { index, imageUrl ->
        Page(index, imageUrl = imageUrl.normalizeImageUrl())
    }
}

@Serializable
internal class TagsBody(
    val tags: List<String>,
)

internal fun String.normalizeImageUrl(): String = when {
    startsWith("http://51.79.78.152") -> replaceFirst(
        "http://51.79.78.152",
        "https://static.geekstations.com.br",
    )
    startsWith("https://51.79.78.152") -> replaceFirst(
        "https://51.79.78.152",
        "https://static.geekstations.com.br",
    )
    else -> this
}

private val CHAPTER_NUMBER = Regex("""\d+(?:[.,]\d+)?""")
