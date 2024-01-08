package eu.kanade.tachiyomi.extension.zh.terrahistoricus

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class THResult<T>(val code: Int, val msg: String, val data: T)

// https://web.hycdn.cn/comic/site/umi.dee612f8.js
@Serializable
data class THComic(
    val cid: String,
    val type: Int,
    val cover: String,
    val title: String,
    val subtitle: String,
    val authors: List<String>,
    val keywords: List<String>? = null,
    val introduction: String? = null,
//  val direction: String? = null,
    val episodes: List<THEpisode>? = null,
    val updateTime: Long? = null, // timestamp in seconds
) {
    private fun getDescription(): String? {
        var result = ""
        if (subtitle.isNotEmpty()) result += "「$subtitle」\n"
        if (introduction != null) result += introduction
        return result.ifEmpty { null }
    }

    private fun getType() = when (type) {
        2 -> listOf("相簿")
        3 -> listOf("四格")
        else -> emptyList() // 1 = Normal
    }

    fun toSManga() = SManga.create().apply {
        url = "/api/comic/$cid"
        title = this@THComic.title
        author = authors.joinToString("、")
        thumbnail_url = cover
        description = getDescription()
        genre = (getType() + keywords.orEmpty()).joinToString(", ").replace("，", ", ")
    }

    fun toSChapterList() = episodes!!.map { episode ->
        SChapter.create().apply {
            url = "/api/comic/$cid/episode/${episode.cid!!}"
            name = if (episode.type != 1) {
                "${EPISODE_TYPES[episode.type]} ${episode.title}"
            } else if (episode.shortTitle.isNullOrBlank()) {
                episode.title
            } else {
                "${episode.shortTitle} ${episode.title}"
            }
        }
    }.apply {
        this[0].date_upload = (updateTime ?: 0L) * 1000
    }

    companion object {
        private val EPISODE_TYPES = arrayOf("", "正篇", "番外", "贺图", "公告")
    }
}

@Serializable
data class THRecentUpdate(
    val coverUrl: String,
    val comicCid: String,
    val title: String,
//  val subtitle: String,
//  val episodeCid: String,
//  val episodeType: Int,
//  val episodeShortTitle: String,
//  val updateTime: Long
) {
    fun toSManga() = SManga.create().apply {
        url = "/api/comic/$comicCid"
        title = this@THRecentUpdate.title
        thumbnail_url = coverUrl
    }
}

@Serializable
data class THEpisode(
    val cid: String? = null,
    val type: Int,
    val shortTitle: String?,
    val title: String, // 作品信息中
//  val likes: Int? = null,
    val pageInfos: List<THPageInfo>? = null, // 章节详情中
)

@Serializable
data class THPageInfo(
//  val width: Int,
//  val height: Int,
    val doublePage: Boolean,
)

@Serializable
data class THPage(
//  val pageNum: Int,
    val url: String,
)
