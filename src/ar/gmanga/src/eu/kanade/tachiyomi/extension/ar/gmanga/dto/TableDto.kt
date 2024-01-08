package eu.kanade.tachiyomi.extension.ar.gmanga.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import uy.kohesive.injekt.injectLazy

@Serializable
data class TableDto(
    val cols: List<String>,
    val rows: List<JsonElement>,
    val isCompact: Boolean,
    val maxLevel: Int,
    val isArray: Boolean? = null,
    val isObject: Boolean? = null,
)

private val json: Json by injectLazy()

private fun TableDto.get(key: String): TableDto? {
    isObject ?: return null

    val index = cols.indexOf(key)
    return json.decodeFromJsonElement(rows[index])
}

fun TableDto.asChapterList() = ChapterListDto(
    // YOLO
    get("releases")!!.rows.map {
        ReleaseDto(
            it.jsonArray[0].jsonPrimitive.int,
            it.jsonArray[1].jsonPrimitive.content,
            it.jsonArray[2].jsonPrimitive.long,
            it.jsonArray[3].jsonPrimitive.int,
            it.jsonArray[4].jsonPrimitive.int,
            it.jsonArray[5].jsonPrimitive.int,
            it.jsonArray[6].jsonArray.map { it.jsonPrimitive.int },
        )
    },
    get("teams")!!.rows.map {
        TeamDto(
            it.jsonArray[0].jsonPrimitive.int,
            it.jsonArray[1].jsonPrimitive.content,
        )
    },
    get("chapterizations")!!.rows.map {
        ChapterDto(
            it.jsonArray[0].jsonPrimitive.int,
            it.jsonArray[1].jsonPrimitive.float,
            it.jsonArray[2].jsonPrimitive.int,
            it.jsonArray[3].jsonPrimitive.content,
            it.jsonArray[4].jsonPrimitive.long,
        )
    },
)
