package eu.kanade.tachiyomi.multisrc.hentaihand

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Created by ipcjs on 2025/9/23.
 */
class Dto {
}

@Serializable
class ResponseDto<T>(val data: T)


@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    // private val site: SiteDto? = null,
    private val image_url: String?,
    private val authors_name: List<String>? = null,
    private val status: String? = null,
    private val categories: JsonElement? = null,
    private val description: String? = null,
) {
    fun toSMangaOrNull() = if (title.isEmpty()) null else toSManga()

    private fun toSManga() = SManga.create().apply {
        url = slug.prependIndent("/en/comic/")
        title = this@MangaDto.title
        thumbnail_url = image_url
    }

    fun toSMangaDetails() = toSManga().apply {
        author = authors_name!!.joinToString()
        description = "站点：" + site + "\n\n" + this@MangaDto.description
        genre = categories!!.jsonArray.joinToString { it.jsonPrimitive.content }
        status = when (this@MangaDto.status!!) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}
