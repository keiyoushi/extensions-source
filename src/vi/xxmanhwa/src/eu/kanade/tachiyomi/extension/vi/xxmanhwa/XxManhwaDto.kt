package eu.kanade.tachiyomi.extension.vi.xxmanhwa

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}

@Serializable
data class CategoryDto(
    @SerialName("term_id") val termId: String,
    val name: String,
)

@Serializable
data class ChapterDto(
    @SerialName("post_modified") val postModified: String,
    @SerialName("post_title") val postTitle: String,
    @SerialName("chap_link") val chapterLink: String,
    @SerialName("member_type") val memberType: String,
) {
    fun toSChapter() =
        SChapter.create().apply {
            url = "/$chapterLink"

            name = postTitle
            if (memberType.isNotBlank()) {
                name += " ($memberType)"
            }

            date_upload =
                runCatching {
                    dateFormat.parse(postModified)!!.time
                }.getOrDefault(0L)
        }
}

@Serializable
data class PageDto(
    val src: String,
    val media: String,
)
