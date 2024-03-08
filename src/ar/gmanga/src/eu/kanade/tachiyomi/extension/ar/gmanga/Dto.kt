package eu.kanade.tachiyomi.extension.ar.gmanga

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
class TableDto(
    private val cols: List<String>,
    private val rows: List<JsonElement>,
    private val isObject: Boolean? = null,
) {
    private fun TableDto.get(key: String, json: Json): TableDto? {
        isObject ?: return null

        val index = cols.indexOf(key)
        return json.decodeFromJsonElement(rows[index])
    }

    fun asChapterList(json: Json) = ChapterListDto(
        get("releases", json)!!.rows.map {
            ReleaseDto(
                it.jsonArray[0].jsonPrimitive.int,
                it.jsonArray[2].jsonPrimitive.long,
                it.jsonArray[3].jsonPrimitive.int,
                it.jsonArray[4].jsonPrimitive.int,
                it.jsonArray[5].jsonPrimitive.int,
            )
        },
        get("teams", json)!!.rows.map {
            TeamDto(
                it.jsonArray[0].jsonPrimitive.int,
                it.jsonArray[1].jsonPrimitive.content,
            )
        },
        get("chapterizations", json)!!.rows.map {
            ChapterDto(
                it.jsonArray[0].jsonPrimitive.int,
                it.jsonArray[1].jsonPrimitive.float,
                it.jsonArray[3].jsonPrimitive.content,
            )
        },
    )
}

class ReleaseDto(
    val id: Int,
    val timestamp: Long,
    val views: Int,
    val chapterizationId: Int,
    val teamId: Int,
)

class TeamDto(
    val id: Int,
    val name: String,
)

class ChapterDto(
    val id: Int,
    val chapter: Float,
    val title: String,
)

class ChapterListDto(
    val releases: List<ReleaseDto>,
    val teams: List<TeamDto>,
    val chapters: List<ChapterDto>,
)
