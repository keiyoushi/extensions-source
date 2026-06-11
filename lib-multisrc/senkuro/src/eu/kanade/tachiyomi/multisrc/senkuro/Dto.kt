package eu.kanade.tachiyomi.multisrc.senkuro

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

@Serializable
class MangasResponseDto(val mangas: ConnectionDto<MangaNodeDto>)

@Serializable
class ConnectionDto<T>(
    val edges: List<EdgeDto<T>>,
    val pageInfo: PageInfoDto,
)

@Serializable
class EdgeDto<T>(val node: T)

@Serializable
class PageInfoDto(
    val hasNextPage: Boolean,
    val endCursor: String? = null,
)

@Serializable
class MangaNodeDto(
    private val slug: String,
    private val titles: List<TitleDto>? = null,
    private val cover: CoverDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = titles?.find { it.lang == "RU" }?.content
            ?: titles?.find { it.lang == "EN" }?.content
            ?: titles?.firstOrNull()?.content ?: ""
        url = slug
        thumbnail_url = cover?.original?.url ?: cover?.preview?.url
    }
}

@Serializable
class CoverDto(
    val original: ImageSizeDto? = null,
    val preview: ImageSizeDto? = null,
)

@Serializable
class ImageSizeDto(val url: String)

@Serializable
class TitleDto(
    val lang: String,
    val content: String,
)

@Serializable
class MangaDetailsResponseDto(val manga: MangaDetailsDto)

@Serializable
class MangaDetailsDto(
    private val slug: String,
    private val titles: List<TitleDto>? = null,
    private val alternativeNames: List<TitleDto>? = null,
    private val localizations: List<LocalizationDto>? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val rating: String? = null,
    private val formats: List<String>? = null,
    private val labels: List<LabelDto>? = null,
    private val cover: CoverDto? = null,
    val branches: List<BranchDto>? = null,
    private val mainStaff: List<MainStaffDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = titles?.find { it.lang == "RU" }?.content
            ?: titles?.find { it.lang == "EN" }?.content
            ?: titles?.firstOrNull()?.content ?: ""
        url = slug
        thumbnail_url = cover?.original?.url ?: cover?.preview?.url

        author = mainStaff?.filter { "STORY" in it.roles }?.joinToString { it.person.name }
        artist = mainStaff?.filter { "ART" in it.roles }?.joinToString { it.person.name }

        description = buildString {
            val altName = alternativeNames?.joinToString(" / ") { it.content }
            if (!altName.isNullOrEmpty()) {
                append("Альтернативные названия:\n", altName, "\n\n")
            }
            append(localizations?.find { it.lang == "RU" }?.extractDescription().orEmpty())
        }

        status = parseStatus(this@MangaDetailsDto.status)
        genre = listOfNotNull(
            getTypeName(type),
            getAgeName(rating),
            formats?.mapNotNull { getFormatName(it) }?.joinToString()?.takeIf { it.isNotEmpty() },
            labels?.joinToString { git -> git.titles.find { it.lang == "RU" }?.content ?: "" }?.takeIf { it.isNotEmpty() },
        ).joinToString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } }
    }
}

@Serializable
class LocalizationDto(
    val lang: String,
    private val description: JsonElement? = null,
) {
    fun extractDescription(): String {
        if (description == null) return ""
        return extractText(description).trim()
    }

    private fun extractText(element: JsonElement): String {
        if (element is JsonArray) {
            return element.joinToString("") { extractText(it) }
        }
        if (element is JsonObject) {
            val type = element["type"]?.jsonPrimitive?.content
            if (type == "text") {
                return element["text"]?.jsonPrimitive?.content ?: ""
            }
            val content = element["content"]?.let { extractText(it) } ?: ""
            if (type == "paragraph") {
                return "$content\n"
            }
            return content
        }
        return ""
    }
}

@Serializable
class LabelDto(val titles: List<TitleDto>)

@Serializable
class BranchDto(val id: String, val primaryBranch: Boolean)

@Serializable
class MainStaffDto(val roles: List<String>, val person: PersonDto)

@Serializable
class PersonDto(val name: String)

@Serializable
class ChaptersResponseDto(val mangaChapters: ConnectionDto<ChapterNodeDto>)

@Serializable
class ChapterNodeDto(
    val slug: String,
    val name: String? = null,
    val number: String? = null,
    val volume: String? = null,
    val createdAt: String? = null,
    val creator: CreatorDto? = null,
)

@Serializable
class CreatorDto(val name: String? = null)

@Serializable
class ChapterPagesResponseDto(val mangaChapter: MangaChapterDto)

@Serializable
class MangaChapterDto(val pages: List<PageNodeDto>? = null)

@Serializable
class PageNodeDto(val image: PageImageDto? = null)

@Serializable
class PageImageDto(
    val original: ImageSizeDto? = null,
    val compress: ImageSizeDto? = null,
)

@Serializable
class FiltersResponseDto(val allLabels: List<FilterLabelDto>)

@Serializable
class FilterLabelDto(
    val id: String,
    val slug: String,
    val rootId: String,
    val titles: List<TitleDto>,
)

private fun parseStatus(status: String?): Int = when (status) {
    "FINISHED" -> SManga.COMPLETED
    "ONGOING", "ANNOUNCE" -> SManga.ONGOING
    "HIATUS" -> SManga.ON_HIATUS
    "CANCELLED" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private fun getTypeName(slug: String?) = when (slug) {
    "MANGA" -> "Манга"
    "MANHWA" -> "Манхва"
    "MANHUA" -> "Маньхуа"
    "COMICS" -> "Комикс"
    "OEL_MANGA" -> "OEL Манга"
    "RU_MANGA" -> "РуМанга"
    else -> null
}

private fun getAgeName(slug: String?) = when (slug) {
    "GENERAL" -> "0+"
    "SENSITIVE" -> "12+"
    "QUESTIONABLE" -> "16+"
    "EXPLICIT" -> "18+"
    else -> null
}

private fun getFormatName(slug: String?) = when (slug) {
    "DIGEST" -> "Сборник"
    "DOUJINSHI" -> "Додзинси"
    "IN_COLOR" -> "В цвете"
    "SINGLE" -> "Сингл"
    "WEB" -> "Веб"
    "WEBTOON" -> "Вебтун"
    "YONKOMA" -> "Ёнкома"
    "SHORT" -> "Short"
    else -> null
}
