package eu.kanade.tachiyomi.extension.en.pururin

import kotlinx.serialization.Serializable

@Serializable
data class Results(
    private val current_page: Int,
    private val data: List<Data>,
    private val last_page: Int,
) : Iterable<Data> by data {
    val hasNext get() = current_page != last_page
}

@Serializable
data class Data(
    private val id: Int,
    val title: String,
    private val slug: String,
) {
    val path get() = "/gallery/$id/$slug"

    val cover get() = "/$id/cover.jpg"
}

@Serializable
data class Gallery(
    val id: Int,
    private val j_title: String,
    private val alt_title: String?,
    private val total_pages: Int,
    private val image_extension: String,
    private val tags: TagList,
) {
    val description get() = "$j_title\n${alt_title ?: ""}".trim()

    val pages get() = (1..total_pages).map { "/$id/$it.$image_extension" }

    val genres get() = tags.Parody +
        tags.Contents +
        tags.Category +
        tags.Character +
        tags.Convention

    val artists get() = tags.Artist

    val authors get() = tags.Circle.ifEmpty { tags.Artist }

    val scanlators get() = tags.Scanlator
}

@Serializable
data class TagList(
    val Artist: List<Tag>,
    val Circle: List<Tag>,
    val Parody: List<Tag>,
    val Contents: List<Tag>,
    val Category: List<Tag>,
    val Character: List<Tag>,
    val Scanlator: List<Tag>,
    val Convention: List<Tag>,
)

@Serializable
data class Tag(private val name: String) {
    override fun toString() = name
}
