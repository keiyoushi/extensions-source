package eu.kanade.tachiyomi.extension.zh.hanabimanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class Comic(
    val id: Int?,
    val title: String?,
    val summary: String?,
    @SerialName("cover_url") val coverUrl: String?,
    // @SerialName("release_date") val releaseDate: String,
    @SerialName("is_finished") val isFinished: Boolean?,
    // @SerialName("lock_status") val lockStatus: String?,
    val authors: List<String>?,
    val region: String?,
    val categories: Tag?,
    val tags: List<Tag>?,
    val chapters: List<Chapter>?,
    // val poster_url: String? = null,
) {

    fun region() = when (region) {
        "jp" -> "日漫"
        "kr" -> "韩漫"
        "us" -> "美漫"
        else -> null
    }

    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@Comic.title!!
        thumbnail_url = coverUrl
        author = authors?.joinToString("，")
        description = summary
        genre = (tags.orEmpty().map { it.name } + listOfNotNull(categories?.name, region())).joinToString()
        status = when (isFinished) {
            true -> SManga.COMPLETED
            false -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject { categories?.let { put("category_id", categories.id) } }
        initialized = tags != null
    }
}

@Serializable
class Tag(val id: Int, val name: String)

@Serializable
class Chapter(
    val id: Int,
    val idx: Int,
    val title: String,
    val category: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("image_count") val size: Int,
) {
    fun toSChapter(cid: String) = SChapter.create().apply {
        url = id.toString()
        name = title
        // chapter_number = idx.toFloat()
        date_upload = Instant.parse(updatedAt).toEpochMilliseconds()
        scanlator = when (category) {
            "normal" -> "连载"
            "special" -> "特典番外"
            "volume" -> "单行本"
            else -> null
        }
        memo = buildJsonObject {
            put("cid", cid)
            put("idx", idx)
            put("size", size)
        }
    }
}

@Serializable
class PagesResult(val urls: List<JsonObject>, val scrambleInfo: ScrambleInfo)

@Serializable
class ScrambleInfo(val ticket: String, val nonce: String, val cols: Int, val rows: Int) {
    companion object {
        fun parse(fragment: String): ScrambleInfo {
            val (ticket, nonce, colsStr, rowsStr) = fragment.split("|").also {
                require(it.size == 4) { "expected #ticket|nonce|cols|rows, got: \"$fragment\"" }
            }
            val cols = colsStr.toIntOrNull() ?: throw IllegalArgumentException("invalid cols: $colsStr")
            val rows = rowsStr.toIntOrNull() ?: throw IllegalArgumentException("invalid rows: $rowsStr")
            require(cols > 0 && rows > 0) { "invalid cols/rows: $cols/$rows" }
            return ScrambleInfo(ticket, nonce, cols, rows)
        }
    }
}
