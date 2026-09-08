package eu.kanade.tachiyomi.extension.zh.hikarinagi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getObject
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.obj
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.collections.firstOrNull
import kotlin.time.Instant

@Serializable
class MangaData(
    val manga: MangaItem,
    val chapters: List<ChapterItem>,
    private val people: JsonArray,
    // private val producers: JsonArray,
    private val tags: JsonArray,
) {
    fun people() = people.associate {
        with(it.obj) {
            val role = getString("role")
            val value = with(getObject("person")) { getStringOrNull("trans_name") ?: getString("name") }
            role to value
        }
    }.takeIf { it.isNotEmpty() }

    // fun producers() = producers.map { it.obj.getObject("producer").getString("name") }

    fun tags() = tags.map { it.obj.getObject("tag").getString("name") }
}

@Serializable
class MangaItem(
    val id: Int,
    val name: String,
    @SerialName("name_cn") val nameCn: String?,
    private val covers: JsonArray,
    // val status: String?,
    @SerialName("serial_status") val serialStatus: String?,
    @SerialName("latest_chapter_at") val latestChapterAt: String?,
    val summary: String?,
) {
    fun toSManga(people: Map<String, String>? = null, tags: List<String>? = null) = SManga.create().apply {
        url = id.toString()
        title = nameCn ?: name
        author = people?.getOrDefault("ORIGINAL_CREATOR", people["AUTHOR"])
        artist = people?.get("ART")
        thumbnail_url = covers.firstOrNull()?.obj?.getObject("media")?.getString("src")?.let {
            if (it.startsWith("http")) it else "${Hikarinagi.IMAGE_BASR_URL}/$it"
        }
        description = summary
        genre = tags?.joinToString()
        status = when (serialStatus) {
            "SERIALIZING" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject { put("updateAt", Instant.tryParse(latestChapterAt)) }
    }
}

@Serializable
class ChapterItem(
    val id: Int,
    val name: String,
    @SerialName("page_count") val size: Int,
    // @SerialName("chapter_type") val chapterType: String, // SERIALIZATION - 连载 | EXTRA - 番外
    // @SerialName("chapter_number") val chapterNumber: String?,
    // @SerialName("volume_number") val volumeNumber: String?,
    // val readable: Boolean,
) {
    fun toSChapter(cid: String, timestamp: Long) = SChapter.create().apply {
        url = id.toString()
        name = this@ChapterItem.name
        scanlator = "${size}P"
        date_upload = timestamp
        memo = buildJsonObject { put("cid", cid) }
    }
}
