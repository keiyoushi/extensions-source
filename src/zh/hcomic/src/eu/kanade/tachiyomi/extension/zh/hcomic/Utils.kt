package eu.kanade.tachiyomi.extension.zh.hcomic

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Response
import kotlin.collections.map

data class HManga(
    val id: String,
    val mediaId: String,
    val title: Title,
    val numPages: Int,
    val author: String,
    val tags: List<Tag>,
    val timestamp: Long,
    val source: String,
) {
    fun filterTags(type: String): String {
        val suffix = tags.filter { it.type == type }.joinToString("、") { it.nameZH }
        val prefix = when (type) {
            "parody" -> "原著"
            "character" -> "角色"
            "group" -> "製作組"
            else -> ""
        }
        return if (suffix.isBlank()) "" else "**$prefix：** $suffix\n"
    }

    fun toSManga(imgUrl: String): SManga = SManga.create().apply {
        url = "$source/$mediaId:$numPages|$timestamp:${tags.find { it.type == "category" }?.nameZH ?: ""}"
        title = this@HManga.title.display
        author = this@HManga.author
        description = filterTags("parody") + filterTags("character") + filterTags("group") + "**页数：** $numPages"
        genre = tags.filter { it.type == "tag" }.joinToString { it.nameZH }
        thumbnail_url = "$imgUrl/$source/$mediaId"
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }
}

data class Title(val japanese: String, val english: String, val pretty: String, val display: String)

data class Tag(val id: Int, val type: String, val name: String, val nameZH: String)

fun parseMangas(data: List<JsonElement>, structIdx: Int): HManga {
    val struct = data[structIdx].jsonObject

    val id = data[struct["id"]!!.jsonPrimitive.int].jsonPrimitive.content
    val mediaId = data[struct["media_id"]!!.jsonPrimitive.int].jsonPrimitive.content
    val source = data[struct["comic_source"]!!.jsonPrimitive.int].jsonPrimitive.content

    // title
    val titleObj = data[struct["title"]!!.jsonPrimitive.int].jsonObject
    val title = Title(
        data[titleObj["japanese"]!!.jsonPrimitive.int].jsonPrimitive.content,
        data[titleObj["english"]!!.jsonPrimitive.int].jsonPrimitive.content,
        data[titleObj["pretty"]!!.jsonPrimitive.int].jsonPrimitive.content,
        data[titleObj["display"]!!.jsonPrimitive.int].jsonPrimitive.content,
    )

    // tag
    val tagIdxs = data[struct["tags"]!!.jsonPrimitive.int].jsonArray
    val allTags = tagIdxs.map { tagIdx ->
        val t = data[tagIdx.jsonPrimitive.int].jsonObject
        val tagId = data[t["id"]!!.jsonPrimitive.int].jsonPrimitive.int
        val type = data[t["type"]!!.jsonPrimitive.int].jsonPrimitive.content
        val name = data[t["name"]!!.jsonPrimitive.int].jsonPrimitive.content
        val nameZH = t["name_zh"]?.let { data[it.jsonPrimitive.int] }?.jsonPrimitive?.contentOrNull ?: name
        Tag(tagId, type, name, nameZH)
    }

    val author = allTags.filter { it.type == "artist" }.joinToString(", ") { it.nameZH }
    val tags = allTags.filter { it.type != "artist" }

    val numPages = data[struct["num_pages"]!!.jsonPrimitive.int].jsonPrimitive.int
    val timestamp = data[struct["upload_date"]!!.jsonPrimitive.int].jsonPrimitive.long

    return HManga(id, mediaId, title, numPages, author, tags, timestamp, source)
}

fun Response.parseAsMangaList(page: Int): Pair<List<HManga>, Boolean> {
    val root = parseAs<JsonObject>()
    val data = root["nodes"]!!.jsonArray[1].jsonObject["data"]!!.jsonArray
    val indexes = data[0].jsonObject
    val pages = indexes["pages"]?.jsonPrimitive?.int

    val comicIndexes = data[indexes["comics"]!!.jsonPrimitive.int].jsonArray.map { it.jsonPrimitive.int }
    val totalPages = pages?.let { data[data[it].jsonObject["pages"]!!.jsonPrimitive.int].jsonPrimitive.int } ?: 1
    return comicIndexes.map { parseMangas(data, it) } to (page < totalPages)
}

fun Response.parseAsManga(): HManga {
    val root = parseAs<JsonObject>()
    val data = root["nodes"]!!.jsonArray[1].jsonObject["data"]!!.jsonArray

    return parseMangas(data, 1)
}
