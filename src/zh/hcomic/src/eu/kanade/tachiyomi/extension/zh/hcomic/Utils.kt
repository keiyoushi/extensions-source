package eu.kanade.tachiyomi.extension.zh.hcomic

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.array
import keiyoushi.utils.get
import keiyoushi.utils.getInt
import keiyoushi.utils.getIntOrNull
import keiyoushi.utils.int
import keiyoushi.utils.long
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Response
import java.net.URLEncoder

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
        url = "/comics/${URLEncoder.encode(this@HManga.title.display, "UTF-8")}/1"
        title = this@HManga.title.display
        author = this@HManga.author
        description = filterTags("parody") + filterTags("character") + filterTags("group") + "**页数：** $numPages"
        genre = tags.filter { it.type == "tag" }.joinToString { it.nameZH }
        thumbnail_url = "$imgUrl/$source/$mediaId"
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        memo = buildJsonObject {
            put("mediaId", "$source/$mediaId")
            put("numPages", numPages)
            put("timestamp", timestamp * 1000L)
            put("category", tags.find { it.type == "category" }?.nameZH ?: "")
        }
        initialized = true
    }
}

data class Title(val japanese: String, val english: String, val pretty: String, val display: String)

data class Tag(val id: Int, val type: String, val name: String, val nameZH: String)

fun parseMangas(data: List<JsonElement>, structIdx: Int): HManga {
    val struct = data[structIdx].obj

    val id = data[struct.getInt("id")].string
    val mediaId = data[struct.getInt("media_id")].string
    val source = data[struct.getInt("comic_source")].string

    val titleObj = data[struct.getInt("title")].obj
    val title = Title(
        data[titleObj.getInt("japanese")].string,
        data[titleObj.getInt("english")].string,
        data[titleObj.getInt("pretty")].string,
        data[titleObj.getInt("display")].string,
    )

    val tagIdxs = data[struct.getInt("tags")].array
    val allTags = tagIdxs.map { tagIdx ->
        val t = data[tagIdx.int].obj
        val tagId = data[t.getInt("id")].int
        val type = data[t.getInt("type")].string
        val name = data[t.getInt("name")].string
        val nameZH = t.getIntOrNull("name_zh")?.let { data[it].string } ?: name
        Tag(tagId, type, name, nameZH)
    }

    val author = allTags.filter { it.type == "artist" }.joinToString(", ") { it.nameZH }
    val tags = allTags.filter { it.type != "artist" }

    val numPages = data[struct.getInt("num_pages")].int
    val timestamp = data[struct.getInt("upload_date")].long

    return HManga(id, mediaId, title, numPages, author, tags, timestamp, source)
}

fun Response.parseAsMangaList(page: Int): Pair<List<HManga>, Boolean> {
    val root = parseAs<JsonObject>()
    val data = root["nodes"]!![1]!!["data"]!!.array
    val indexes = data[0].obj
    val pages = indexes.getIntOrNull("pages")

    val comicIndexes = data[indexes.getInt("comics")].array.map { it.int }
    val totalPages = pages?.let { data[data[it].obj.getInt("pages")].int } ?: 1
    return comicIndexes.map { parseMangas(data, it) } to (page < totalPages)
}

fun Response.parseAsManga(): HManga {
    val root = parseAs<JsonObject>()
    val data = root["nodes"]!![1]!!["data"]!!.array
    return parseMangas(data, 1)
}
