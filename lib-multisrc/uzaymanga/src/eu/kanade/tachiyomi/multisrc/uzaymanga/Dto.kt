package eu.kanade.tachiyomi.multisrc.uzaymanga

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import kotlin.time.Instant

@Serializable
class SvelteResponse(
    private val nodes: List<SvelteNode>? = null,
) {
    fun getData(): JsonArray? = nodes?.lastOrNull { it.type == "data" }?.data
}

@Serializable
class SvelteNode(
    val type: String,
    val data: JsonArray? = null,
)

/**
 * Helper class to navigate SvelteKit's 'devalue' serialized data array.
 * In this format, object properties are represented as integer indices pointing to other elements in the array.
 */
class SvelteData(private val array: JsonArray) {

    fun getObject(index: Int) = array.getOrNull(index)?.let {
        if (it !is JsonPrimitive && it !is JsonNull) it.jsonObject else null
    }

    fun getArray(index: Int) = array.getOrNull(index)?.let {
        if (it !is JsonPrimitive && it !is JsonNull) it.jsonArray else null
    }

    fun getString(index: Int): String? {
        val el = array.getOrNull(index) ?: return null
        if (el !is JsonPrimitive || el is JsonNull) return null
        return el.content
    }

    fun getInt(index: Int): Int? {
        val el = array.getOrNull(index) ?: return null
        if (el !is JsonPrimitive || el is JsonNull) return null
        return el.intOrNull
    }

    fun resolveObject(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getObject(it) }
    fun resolveArray(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getArray(it) }
    fun resolveString(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getString(it) }
    fun resolveInt(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getInt(it) }

    fun resolveDate(node: JsonObject, key: String): Long {
        val dateArray = resolveArray(node, key) ?: return 0L
        if (dateArray.size < 2) return 0L
        val el = dateArray[1]
        val dateString = if (el is JsonPrimitive && el !is JsonNull) el.content else return 0L
        return Instant.tryParse(dateString)
    }
}

fun Response.parseSvelteRoot(): Pair<SvelteData, JsonObject>? {
    val dataArray = parseAs<SvelteResponse>().getData() ?: return null
    val svelte = SvelteData(dataArray)
    val root = svelte.getObject(0) ?: return null
    return svelte to root
}

fun SvelteData.toSManga(seriesObj: JsonObject, baseUrl: String, cdnUrl: String?): SManga? {
    val name = resolveString(seriesObj, "name") ?: return null
    val slug = resolveString(seriesObj, "slug") ?: return null
    val imagePath = resolveString(seriesObj, "image") ?: ""

    return SManga.create().apply {
        title = name
        thumbnail_url = resolveImageUrl(imagePath, baseUrl, cdnUrl)
        url = "/manga/$slug"
    }
}

fun SvelteData.toSMangaList(indices: JsonArray, baseUrl: String, cdnUrl: String?): List<SManga> = indices.mapNotNull {
    val mangaIdx = it.jsonPrimitive.intOrNull ?: return@mapNotNull null
    val mangaObj = getObject(mangaIdx) ?: return@mapNotNull null
    toSManga(mangaObj, baseUrl, cdnUrl)
}

fun resolveImageUrl(imagePath: String, baseUrl: String, cdnUrl: String?): String {
    if (imagePath.startsWith("http")) return imagePath
    val baseImgUrl = cdnUrl?.removeSuffix("/") ?: baseUrl.removeSuffix("/")
    return "$baseImgUrl/${imagePath.removePrefix("/")}"
}
