package eu.kanade.tachiyomi.extension.all.izneo

import kotlinx.serialization.Serializable

@Serializable
data class Series(
    val name: String,
    val url: String,
    private val id: String,
    private val version: Int,
    private val synopsis: String,
    private val gender: String,
    private val target: Target,
    val authors: List<Author>,
) {
    val genres: String
        get() = "$gender, $target"

    val cover: String
        get() = "/images/serie/$id.jpg?v=$version"

    override fun toString() =
        synopsis.replace("\n          ", " ").replace("<br />", "")
}

@Serializable
data class Target(private val name: String) {
    override fun toString() = name
}

@Serializable
data class Author(private val nickname: String) {
    override fun toString() = nickname
}

@Serializable
data class Album(
    private val id: String,
    private val title: String,
    private val chapter: String,
    val publicationDate: String,
    private val fullAvailable: Boolean,
    private val inUserLibrary: Boolean,
    private val inUserSubscription: Boolean,
) {
    val number: Float
        get() = chapter.toFloat()

    val path: String
        get() = "/episode-$chapter-$id/read/1"

    private inline val isLocked: Boolean
        get() = !fullAvailable && !(inUserLibrary || inUserSubscription)

    override fun toString() =
        title + if (isLocked) " \uD83D\uDD12" else ""
}

@Serializable
data class AlbumPage(
    val albumPageNumber: Int,
    private val key: String,
    private val iv: String,
) {
    override fun toString() =
        "/$albumPageNumber?type=full&key=${key.urlSafe}&iv=${iv.urlSafe}"

    private inline val String.urlSafe: String
        get() = replace('+', '-').replace('/', '_')
}
