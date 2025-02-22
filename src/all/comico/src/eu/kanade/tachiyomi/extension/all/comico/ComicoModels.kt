package eu.kanade.tachiyomi.extension.all.comico

import kotlinx.serialization.Serializable

@Serializable
data class ContentInfo(
    val id: Int,
    val name: String,
    val description: String,
    val original: Boolean,
    val exclusive: Boolean,
    val mature: Boolean,
    val status: String? = null,
    val genres: List<Genre>? = null,
    val authors: List<Author>? = null,
    private val thumbnails: List<Thumbnail>,
) {
    val cover: String
        get() = thumbnails[0].toString()
}

@Serializable
data class Thumbnail(private val url: String) {
    override fun toString() = url
}

@Serializable
data class Author(private val name: String, private val role: String) {
    val isAuthor: Boolean
        get() = role == "creator" ||
            role == "writer" ||
            role == "original_creator"

    val isArtist: Boolean
        get() = role == "creator" ||
            role == "artist" ||
            role == "studio" ||
            role == "assistant"

    override fun toString() = name
}

@Serializable
data class Genre(private val name: String) {
    override fun toString() = name
}

@Serializable
data class Chapter(
    val id: Int,
    val name: String,
    val publishedAt: String,
    private val salesConfig: SalesConfig,
    private val hasTrial: Boolean,
    private val activity: Activity,
) {
    val isAvailable: Boolean
        get() = salesConfig.free || hasTrial || activity.owned
}

@Serializable
data class SalesConfig(val free: Boolean)

@Serializable
data class Activity(val rented: Boolean, val unlocked: Boolean) {
    inline val owned: Boolean
        get() = rented || unlocked
}

@Serializable
data class ChapterImage(
    val sort: Int,
    val url: String,
    val parameter: String,
)
