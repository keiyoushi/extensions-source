package eu.kanade.tachiyomi.extension.ja.mokuro

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CatalogDto(
    val series: List<CatalogSeriesDto>,
)

@Serializable
class CatalogSeriesDto(
    @SerialName("series_title") val seriesTitle: String,
    @SerialName("external_ids") private val externalIds: ExternalIdsDto? = null,
    private val titles: TitlesDto? = null,
    private val synonyms: List<String> = emptyList(),
    val tag: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun displayTitle(pref: String = "native"): String {
        val base = when (pref) {
            "english" -> titles?.english ?: titles?.romaji ?: titles?.native ?: seriesTitle
            "romaji" -> titles?.romaji ?: titles?.english ?: titles?.native ?: seriesTitle
            "folder" -> seriesTitle
            else -> titles?.native ?: titles?.romaji ?: titles?.english ?: seriesTitle
        }
        if (tag.isNullOrBlank()) return base
        val stripped = stripOuterBrackets(tag)
        return if (stripped.isNotBlank()) "$base ($stripped)" else base
    }

    fun matches(query: String, pref: String = "native"): Boolean {
        val q = query.trim()
        return displayTitle(pref).contains(q, ignoreCase = true) ||
            seriesTitle.contains(q, ignoreCase = true) ||
            (titles?.native?.contains(q, ignoreCase = true) == true) ||
            (titles?.romaji?.contains(q, ignoreCase = true) == true) ||
            (titles?.english?.contains(q, ignoreCase = true) == true) ||
            synonyms.any { it.contains(q, ignoreCase = true) }
    }

    fun toSManga(pref: String = "native"): SManga = SManga.create().apply {
        title = displayTitle(pref)
        url = seriesTitle
    }

    fun fillDetails(manga: SManga, pref: String = "native") {
        manga.title = displayTitle(pref)
        manga.genre = tag
        manga.description = buildString {
            titles?.english?.takeIf { it.isNotBlank() && it != manga.title }?.let {
                append("English: ").appendLine(it)
            }
            titles?.romaji?.takeIf { it.isNotBlank() && it != manga.title }?.let {
                append("Romaji: ").appendLine(it)
            }
            titles?.native?.takeIf { it.isNotBlank() && it != manga.title }?.let {
                append("Native: ").appendLine(it)
            }
            if (synonyms.isNotEmpty()) {
                append("Synonyms: ").appendLine(synonyms.joinToString())
            }
            val anilistId = externalIds?.anilist
            val malId = externalIds?.mal
            if (anilistId != null || malId != null) {
                appendLine()
                anilistId?.let {
                    appendLine("[AniList](https://anilist.co/manga/$it)")
                }
                malId?.let {
                    appendLine("[MyAnimeList](https://myanimelist.net/manga/$it)")
                }
            }
        }.trim().ifEmpty { null }
    }
}

@Serializable
class ExternalIdsDto(
    val anilist: Int? = null,
    val mal: Int? = null,
)

@Serializable
class TitlesDto(
    val native: String? = null,
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
class SeriesDetailDto(
    @SerialName("series_title") val seriesTitle: String,
    private val titles: TitlesDto? = null,
    val volumes: List<VolumeDto> = emptyList(),
) {
    fun displayTitle(): String = titles?.native ?: titles?.romaji ?: titles?.english ?: seriesTitle
}

@Serializable
class VolumeDto(
    @SerialName("volume_title") val volumeTitle: String,
    @SerialName("mokuro_modified") private val mokuroModified: Long? = null,
) {
    fun toSChapter(seriesTitle: String) = SChapter.create().apply {
        name = this@VolumeDto.volumeTitle
        chapter_number = parseChapterNumber(this@VolumeDto.volumeTitle)
        date_upload = mokuroModified?.times(1000L) ?: 0L
        url = "$seriesTitle|${this@VolumeDto.volumeTitle}"
    }

    private fun parseChapterNumber(name: String): Float = chapterNumberRegex.findAll(name).lastOrNull()?.value?.toFloatOrNull() ?: -1f
}

@Serializable
class MokuroDto(
    val pages: List<MokuroPageDto>,
)

@Serializable
class MokuroPageDto(
    @SerialName("img_path") val imgPath: String,
)

@Serializable
class ImageRequest(
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val method: Int,
)

private val chapterNumberRegex = """(\d+(\.\d+)?)""".toRegex()

// Mirrors catalog.js stripOuterBracketPair: strips exactly one surrounding bracket pair.
private val bracketPairs = listOf('(' to ')', '[' to ']', '（' to '）', '【' to '】')

private fun stripOuterBrackets(value: String): String {
    for ((open, close) in bracketPairs) {
        if (value.length > 1 && value.first() == open && value.last() == close) {
            return value.substring(1, value.length - 1).trim()
        }
    }
    return value
}
