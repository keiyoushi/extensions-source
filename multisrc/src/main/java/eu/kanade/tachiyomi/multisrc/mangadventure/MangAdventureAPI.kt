package eu.kanade.tachiyomi.multisrc.mangadventure

/** Generic results wrapper schema. */
@kotlinx.serialization.Serializable
internal class Results<T>(
    private val results: List<T>,
) : Iterable<T> by results

/** Generic paginator schema. */
@kotlinx.serialization.Serializable
internal class Paginator<T>(
    val last: Boolean,
    private val results: List<T>,
) : Iterable<T> by results

/** Page model schema. */
@kotlinx.serialization.Serializable
internal data class Page(
    private val id: Int,
    val image: String,
    val number: Int,
    val url: String,
) {
    override fun equals(other: Any?) =
        this === other || other is Page && id == other.id

    override fun hashCode() = id
}

/** Chapter model schema. */
@kotlinx.serialization.Serializable
internal data class Chapter(
    val id: Int,
    val title: String,
    val number: Float,
    val volume: Int?,
    val published: String,
    val final: Boolean,
    val series: String,
    val groups: List<String>,
    val full_title: String,
) {
    override fun equals(other: Any?) =
        this === other || other is Chapter && id == other.id

    override fun hashCode() = id
}

/** Series model schema. */
@kotlinx.serialization.Serializable
internal data class Series(
    val slug: String,
    val title: String,
    val cover: String,
    val description: String? = null,
    val status: String? = null,
    val licensed: Boolean? = null,
    val aliases: List<String>? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val categories: List<String>? = null,
) {
    override fun equals(other: Any?) =
        this === other || other is Series && slug == other.slug

    override fun hashCode() = slug.hashCode()
}
