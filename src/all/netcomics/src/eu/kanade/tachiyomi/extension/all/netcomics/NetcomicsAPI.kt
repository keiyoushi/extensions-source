@file:Suppress("PrivatePropertyName")

package eu.kanade.tachiyomi.extension.all.netcomics

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

internal const val API_URL = "https://beta-api.netcomics.com/api/v1"

private const val CDN_URL =
    "https://cdn.netcomics.com/img/fill/324/0/sm/0/plain/s3://"

private val isoDate by lazy {
    SimpleDateFormat("yyyy-MM-d'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
}

@Serializable
data class Title(
    private val title_id: Int,
    private val site: String,
    private val title_name: String,
    private val title_slug: String,
    private val story: String,
    private val genre: String,
    private val age_grade: String,
    private val is_end: String,
    private val v_cover_img: String,
    private val author_story_arr: List<Author>,
    private val author_picture_arr: List<Author>,
    private val author_origin_arr: List<Author>,
) {
    val slug: String
        get() = "$title_slug|$title_id"

    val description: String?
        get() = Jsoup.parse(story).text()

    val thumbnail: String
        get() = CDN_URL + v_cover_img

    val genres: String
        get() = "$genre, $age_grade+"

    val authors: String
        get() = (author_story_arr + author_origin_arr).names

    val artists: String
        get() = author_picture_arr.names

    val isCompleted: Boolean
        get() = is_end == "Y"

    override fun toString() = title_name

    private inline val List<Author>.names: String
        get() = joinToString { if (site == "KR") it.text else it.text_en }
}

@Serializable
data class Author(val text: String, val text_en: String)

@Serializable
data class Chapter(
    private val chapter_id: Int,
    private val chapter_no: Int,
    private val chapter_name: String,
    private val created_at: String,
    private val title_id: Int,
    private val is_free: String,
    private val is_order: String? = null,
) {
    val path: String
        get() = "$title_id/$chapter_id"

    val number: Float
        get() = chapter_no.toFloat()

    val timestamp: Long
        get() = isoDate.parse(created_at)?.time ?: 0L

    private inline val isLocked: Boolean
        get() = is_free == "N" && is_order != "Y"

    override fun toString() = buildString {
        if (chapter_name.isEmpty()) {
            append("Ch.")
            append(chapter_no)
        } else {
            append(chapter_name)
        }
        if (isLocked) append(" \uD83D\uDD12")
    }
}

@Serializable
data class PageList(private val images: List<Image>) : List<Image> by images

@Serializable
data class Image(val seq: Int, private val image_url: String) {
    override fun toString() = image_url
}
