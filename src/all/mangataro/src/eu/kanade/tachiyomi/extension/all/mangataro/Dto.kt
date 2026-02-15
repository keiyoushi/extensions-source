package eu.kanade.tachiyomi.extension.all.mangataro

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
@Suppress("unused")
class SearchPayload(
    private val page: Int,
    private val search: String,
    @Serializable(with = StringifiedListSerializer::class)
    private val years: List<Int>,
    @Serializable(with = StringifiedListSerializer::class)
    private val genres: List<Int>,
    @Serializable(with = StringifiedListSerializer::class)
    private val types: List<String>,
    @Serializable(with = StringifiedListSerializer::class)
    private val statuses: List<String>,
    private val sort: String,
    private val genreMatchMode: String,
)

class StringifiedListSerializer<T>(elementSerializer: KSerializer<T>) : JsonTransformingSerializer<List<T>>(ListSerializer(elementSerializer)) {

    override fun transformSerialize(element: JsonElement) = JsonPrimitive(element.toJsonString())
}

@Serializable
@Suppress("unused")
class SearchQueryPayload(
    val limit: Int,
    val query: String,
)

@Serializable
class SearchQueryResponse(
    val results: List<Manga>,
) {
    @Serializable
    class Manga(
        val id: Int,
        val slug: String,
        val title: String,
        val thumbnail: String,
        val type: String,
        val description: String,
        val status: String,
    )
}

@Serializable
class BrowseManga(
    val id: String,
    val url: String,
    val title: String,
    val cover: String,
    val type: String,
    val description: String,
    val status: String,
)

@Serializable
data class MangaUrl(
    val id: String,
    val slug: String,
    val group: Long? = null,
)

@Serializable
class MangaDetails(
    val id: Int,
    val slug: String,
    val title: Rendered,
    val content: Rendered,
    val type: String,
    @SerialName("_embedded")
    val embedded: Embedded,
)

@Serializable
class Embedded(
    @SerialName("wp:featuredmedia")
    val featuredMedia: List<Thumbnail>,
    @SerialName("wp:term")
    private val terms: List<List<Term>>,
) {
    fun getTerms(type: String): List<String> = terms.find { it.firstOrNull()?.taxonomy == type }.orEmpty().map { it.name }
}

@Serializable
class Term(
    val name: String,
    val taxonomy: String,
)

@Serializable
class Thumbnail(
    @SerialName("source_url")
    val url: String,
)

@Serializable
class Rendered(
    val rendered: String,
)

@Serializable
class ChapterList(
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    val url: String,
    val chapter: String,
    val title: String? = null,
    val date: String,
    @SerialName("group_name")
    val groupName: String? = null,
    val language: String,
)

@Serializable
class Pages(
    val images: List<String>,
)

@Serializable
class ProjectList(
    val titles: List<MangaDto>,
) {
    @Serializable
    class MangaDto(
        @SerialName("manga_id")
        val id: Long,
        @SerialName("manga_title")
        val title: String,
        @SerialName("group_id")
        val group: Long,
        @SerialName("manga_slug")
        val slug: String,
        @SerialName("cover_url")
        val cover: String,
    ) {
        fun toSManga() = SManga.create().apply {
            title = this@MangaDto.title
            url = MangaUrl(id.toString(), slug, group).toJsonString()
            thumbnail_url = cover
        }
    }
}
