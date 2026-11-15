package eu.kanade.tachiyomi.extension.pt.animexnovel

import eu.kanade.tachiyomi.extension.pt.animexnovel.AnimeXNovel.Companion.supportsTypeSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

@Serializable
class MangaDto(
    val name: JsonElement,
    val alternateName: JsonElement,
    val author: Value? = null,
    val illustrator: Value? = null,
    val genre: JsonElement,
    val description: String? = null,
    val creativeWorkStatus: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        this.title = when (name) {
            is JsonArray -> name[0].string
            else -> name.string
        }.titleCleanUp()

        this.description = buildString {
            appendLine(this@MangaDto.description)
            val titles = when {
                alternateName.isArray -> alternateName.joinToString()
                else -> alternateName.string
            }.titleCleanUp()
            appendLine("\n\nNomes Alternativos: $titles")
        }
        genre = when {
            this@MangaDto.genre.isArray -> this@MangaDto.genre.joinToString()
            else -> this@MangaDto.genre.string
        }
        status = when (creativeWorkStatus?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "published" -> SManga.PUBLISHING_FINISHED
            else -> SManga.UNKNOWN
        }

        author = this@MangaDto.author?.name
        artist = illustrator?.name
    }

    private fun String.titleCleanUp() = replace(titleSuffix, "").trim()

    private fun JsonElement.joinToString() = jsonArray.joinToString { it.string }

    companion object {
        val titleSuffix = """\((${supportsTypeSource.joinToString("|")})\)""".toRegex(RegexOption.IGNORE_CASE)
    }
}

@Serializable
class ChapterDto(
    private val title: String,
    private val id: Long,
    private val link: String,
) {

    fun toSChapter() = SChapter.create().apply {
        name = title.substringAfter("–").trim()
        url = id.toString()
        memo = buildJsonObject {
            put("link", link)
        }
    }
}

@Serializable
class Value(
    val name: String,
)

@Serializable
class BoxValue(
    val name: String,
    val id: String = name,
)

@Serializable
class FilterOptionsDTO(
    val options: List<Pair<String, List<BoxValue>>>,
)

private val JsonElement.isArray get() = this is JsonArray
