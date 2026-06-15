package eu.kanade.tachiyomi.multisrc.senkuro

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
class TachiyomiSearchResponseDto(val mangaTachiyomiSearch: TachiyomiSearchesDto)

@Serializable
class TachiyomiSearchesDto(val mangas: List<TachiyomiSearchMangaDto>)

@Serializable
class TachiyomiSearchMangaDto(
    val id: String,
    val slug: String,
    val titles: List<TitleDto>? = null,
    val originalName: TitleDto? = null,
    val alternativeNames: List<TitleDto>? = null,
    val cover: CoverDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = titles?.find { it.lang == "RU" }?.content
            ?: titles?.find { it.lang == "EN" }?.content
            ?: titles?.firstOrNull()?.content ?: ""
        url = "$id,,$slug"
        thumbnail_url = cover?.original?.url
    }
}

@Serializable
class CoverDto(
    val original: ImageSizeDto? = null,
)

@Serializable
class ImageSizeDto(val url: String)

@Serializable
class TitleDto(
    val lang: String,
    val content: String,
)

@Serializable
class TachiyomiMangaInfoResponseDto(val mangaTachiyomiInfo: TachiyomiMangaDto? = null)

@Serializable
class TachiyomiMangaDto(
    val id: String,
    val slug: String,
    val titles: List<TitleDto>? = null,
    val originalName: TitleDto? = null,
    val alternativeNames: List<TitleDto>? = null,
    val localizations: List<LocalizationDto>? = null,
    val type: String? = null,
    val status: String? = null,
    val rating: String? = null,
    val formats: List<String>? = null,
    val labels: List<LabelDto>? = null,
    val mainStaff: List<MainStaffDto>? = null,
    val translationStatus: String? = null,
    val cover: CoverDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = titles?.find { it.lang == "RU" }?.content
            ?: titles?.find { it.lang == "EN" }?.content
            ?: titles?.firstOrNull()?.content ?: ""
        url = "$id,,$slug"
        thumbnail_url = cover?.original?.url

        author = mainStaff?.filter { "STORY" in it.roles || "STORY_AND_ART" in it.roles || "ORIGINAL_CREATOR" in it.roles }?.joinToString { it.person.name }
        artist = mainStaff?.filter { "ART" in it.roles || "STORY_AND_ART" in it.roles }?.joinToString { it.person.name }

        description = buildString {
            val altName = alternativeNames?.joinToString(" / ") { it.content }
            if (!altName.isNullOrEmpty()) {
                append("Альтернативные названия:\n", altName, "\n\n")
            }
            append(localizations?.find { it.lang == "RU" }?.description.orEmpty())
        }

        status = parseStatus(this@TachiyomiMangaDto.status)
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
    val description: String? = null,
)

@Serializable
class LabelDto(val id: String, val rootId: String? = null, val slug: String, val titles: List<TitleDto>)

@Serializable
class MainStaffDto(val roles: List<String>, val person: PersonDto)

@Serializable
class PersonDto(val name: String)

@Serializable
class TachiyomiChaptersResponseDto(val mangaTachiyomiChapters: TachiyomiChaptersDto)

@Serializable
class TachiyomiChaptersDto(
    val message: String? = null,
    val chapters: List<TachiyomiChapterDto> = emptyList(),
    val teams: List<TachiyomiTeamDto> = emptyList(),
)

@Serializable
class TachiyomiChapterDto(
    val id: String,
    val branchId: String,
    val teamIds: List<String>,
    val slug: String,
    val name: String? = null,
    val number: String,
    val volume: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
class TachiyomiTeamDto(
    val id: String,
    val slug: String,
    val name: String,
)

@Serializable
class TachiyomiChapterPagesResponseDto(val mangaTachiyomiChapterPages: TachiyomiChapterPagesDto)

@Serializable
class TachiyomiChapterPagesDto(val pages: List<TachiyomiChapterPageDto>)

@Serializable
class TachiyomiChapterPageDto(val url: String)

@Serializable
class TachiyomiSearchFiltersResponseDto(val mangaTachiyomiSearchFilters: TachiyomiSearchFiltersDto)

@Serializable
class TachiyomiSearchFiltersDto(val labels: List<LabelDto>)

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
