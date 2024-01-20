package eu.kanade.tachiyomi.extension.ja.rawz

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import java.util.Locale

@Serializable
data class Data<T>(
    val data: T,
)

@Serializable
data class Manga(
    val id: Int,
    val name: String,
    val slug: String,
    val description: String? = null,
    val status: String? = null,
    val type: String? = null,
    val image: String? = null,
    @Serializable(with = EmptyArrayOrTaxonomySerializer::class)
    val taxonomy: Taxonomy,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug.$id"
        thumbnail_url = image
        title = name
        description = this@Manga.description
        genre = (
            taxonomy.genres.map {
                it.name
            }.let {
                type?.run {
                    it.plus(
                        this.replaceFirstChar {
                            if (it.isLowerCase()) {
                                it.titlecase(Locale.getDefault())
                            } else {
                                it.toString()
                            }
                        },
                    )
                }
            }
            )?.joinToString()
        status = when (this@Manga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
data class Taxonomy(
    val genres: List<Genre>,
)

object EmptyArrayOrTaxonomySerializer : JsonTransformingSerializer<Taxonomy>(Taxonomy.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return if (element is JsonArray) {
            JsonObject(mapOf(Pair("genres", JsonArray(emptyList()))))
        } else {
            element
        }
    }
}

@Serializable
data class Genre(
    val id: Int,
    val name: String,
)

@Serializable
data class Chapter(
    val id: Int,
    val name: String,
    val slug: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Pages(
    val images: List<Url>,
)

@Serializable
data class Url(
    val url: String,
)
