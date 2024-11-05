package eu.kanade.tachiyomi.extension.vi.vlogtruyen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterDTO(
    val status: Boolean,
    val data: Data,
)

@Serializable
data class Data(
    @SerialName("historyHtml") val historyHtml: String,
    @SerialName("user") val user: User? = null,
    @SerialName("userHtml") val userHtml: String,
    @SerialName("bookmark_manga") val bookmarkManga: Boolean,
    @SerialName("alertWarningHtml") val alertWarningHtml: String,
    @SerialName("chaptersHtml") val chaptersHtml: String,
    @SerialName("comments") val comments: String,
)

@Serializable
data class User(
    @SerialName("username") val username: String,
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("status") val status: Boolean,
    @SerialName("type") val type: Int,
    @SerialName("image") val image: String,
    @SerialName("utm_source") val utmSource: String,
)
