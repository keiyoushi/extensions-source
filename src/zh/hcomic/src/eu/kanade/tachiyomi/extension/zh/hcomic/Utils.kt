package eu.kanade.tachiyomi.extension.zh.hcomic

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import okhttp3.Response


fun Response.parseAsSManga() = parseComicsFromResponse(parseAs<JsonObject>()).map { comic ->
    SManga.create().apply {
        // 基础信息
        url = comic["mediaId"].toString()
        title = comic["titleDisplay"]?.toString() ?: "无标题"

        // 从标签中提取 artist, author, genre
        val tags = comic["tags"] as? List<Map<String, Any?>> ?: emptyList()
        // 提取 artist（type = "artist"）
        artist = tags.find { it["type"] == "artist" }?.get("name")?.toString()
        // 提取 author（type = "author" 或 "group"，这里按常见规则处理）
        author = tags.find { it["type"] == "author" || it["type"] == "group" }?.get("name")?.toString()
        // genre：将 type 为 "tag" 的标签名拼接（根据网站情况可调整）
        genre = tags.filter { it["type"] == "tag" }
            .mapNotNull { it["name"]?.toString() }
            .joinToString(", ")

        // 状态：可根据是否存在特定标签判断，默认 UNKNOWN
        status = SManga.UNKNOWN // 若需要可从 tags 中判断如 "completed" 等

        // 缩略图 URL：通常可通过 mediaId 构造（以下为示例拼写，请根据真实网站规则修改）
        thumbnail_url = comic["mediaId"]?.let { "https://example.com/galleries/$it/cover.jpg" }

        // 更新策略（默认普通策略）
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        // 标记已初始化
        initialized = true

        // 描述：可提取 tags 中的某些信息，暂时留空
        description = null
    }
}

/**
 * 从完整响应 JSON 中解析漫画数据
 * @param responseJson 完整的响应 JSON 对象（例如调用你的解析方法得到的 JsonObject）
 * @return 漫画对象列表
 */
private fun parseComicsFromResponse(responseJson: JsonObject): List<Map<String, Any?>> {
    // 获取 nodes 数组
    val nodesArray = responseJson["nodes"]?.jsonArray ?: return emptyList()
    // 取第二个节点（索引1），其 type 应为 "data"
    val dataNode = nodesArray.getOrNull(1)?.jsonObject ?: return emptyList()
    val dataArray = dataNode["data"]?.jsonArray ?: return emptyList()
    return extractComics(dataArray)
}

// 提取逻辑（与之前相同，但标记为 private）
private fun extractComics(dataArray: JsonArray): List<Map<String, Any?>> {
    val comics = mutableListOf<Map<String, Any?>>()

    fun getByIndex(idx: Int): JsonElement? = dataArray.getOrNull(idx)

    for (element in dataArray) {
        val obj = element.jsonObjectOrNull() ?: continue
        if (!obj.containsKey("_id") || !obj.containsKey("title")) continue

        fun getInt(key: String): Int? = obj[key]?.jsonPrimitive?.int

        val titleIdx = getInt("title")
        val tagsIdx = getInt("tags")
        val mediaIdIdx = getInt("media_id")
        val numPagesIdx = getInt("num_pages")
        val uploadDateIdx = getInt("upload_date")

        val mediaId = mediaIdIdx?.let { getByIndex(it) }?.let { extractValue(it) }
        val numPages = numPagesIdx?.let { getByIndex(it) }?.let { extractValue(it) }
        val uploadDate = uploadDateIdx?.let { getByIndex(it) }?.let { extractValue(it) }

        var titleDisplay: Any? = null
        if (titleIdx != null) {
            val titleObj = getByIndex(titleIdx)?.jsonObjectOrNull()
            val displayIdx = titleObj?.get("display")?.jsonPrimitive?.int
            titleDisplay = displayIdx?.let { getByIndex(it) }?.let { extractValue(it) }
        }

        val tags = mutableListOf<Map<String, Any?>>()
        if (tagsIdx != null) {
            val tagIndexArray = getByIndex(tagsIdx)?.jsonArray
            if (tagIndexArray != null) {
                for (tagIdxElement in tagIndexArray) {
                    val tagIdx = tagIdxElement.jsonPrimitive.int
                    val tagObj = getByIndex(tagIdx)?.jsonObjectOrNull() ?: continue

                    val typeIdx = tagObj["type"]?.jsonPrimitive?.int
                    val nameIdx = tagObj["name"]?.jsonPrimitive?.int
                    val nameZhIdx = tagObj["name_zh"]?.jsonPrimitive?.int

                    val type = typeIdx?.let { getByIndex(it) }?.let { extractValue(it) }
                    val nameRaw = nameIdx?.let { getByIndex(it) }?.let { extractValue(it) }
                    val nameZh = nameZhIdx?.let { getByIndex(it) }?.let { extractValue(it) }

                    val finalName = when {
                        nameZh != null && nameZh.toString().isNotBlank() -> nameZh
                        else -> nameRaw
                    }

                    tags.add(mapOf(
                        "type" to type,
                        "name" to finalName
                    ))
                }
            }
        }

        comics.add(mapOf(
            "mediaId" to mediaId,
            "numPages" to numPages,
            "uploadDate" to uploadDate,
            "titleDisplay" to titleDisplay,
            "tags" to tags
        ))
    }

    return comics
}

private fun extractValue(element: JsonElement): Any? = when {
    element is JsonPrimitive -> {
        when {
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
    }
    else -> null
}

private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject
