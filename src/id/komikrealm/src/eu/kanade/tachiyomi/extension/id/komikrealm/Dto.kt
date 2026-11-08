package eu.kanade.tachiyomi.extension.id.komikrealm

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

// Qwik serializes data as a flat list of values: objects and arrays hold the base-36 indexes of their
// members in that list, and a string that starts with a control character is a typed value
// (\u0001 for undefined, \u0006 for a date...).
@Serializable
class QwikData(
    @SerialName("_entry") private val entry: String,
    @SerialName("_objs") private val objs: JsonArray,
) {
    fun decode(): JsonElement = resolve(entry)

    private fun resolve(index: String): JsonElement = when (val value = objs[index.toInt(36)]) {
        is JsonObject -> JsonObject(value.mapValues { resolve(it.value.jsonPrimitive.content) })
        is JsonArray -> JsonArray(value.map { resolve(it.jsonPrimitive.content) })
        is JsonPrimitive -> {
            val text = value.contentOrNull
            when {
                !value.isString || text.isNullOrEmpty() || text[0] >= ' ' -> value
                text[0] == '\u0001' -> JsonNull
                else -> JsonPrimitive(text.substring(1))
            }
        }
    }
}

@Serializable
class RouteData(val loaders: Map<String, JsonElement>)

@Serializable
class Series(
    private val title: String,
    private val slug: String,
    private val type: String,
    private val poster: String? = null,
    private val synopsis: String? = null,
    private val alter: String? = null,
    private val status: String? = null,
    private val chapters: List<Chapter> = emptyList(),
    @SerialName("taxonomy_relation") private val taxonomies: List<TaxonomyRelation> = emptyList(),
) {
    // Manhwa, Manhua and Doujinshi have their own path; the site serves the other types (Manga, Webtoon) under /manga/
    private val path get() = "/${type.lowercase().takeIf { it in OWN_PATHS } ?: "manga"}/$slug/"

    fun toSManga() = SManga.create().apply {
        url = path
        title = this@Series.title
        thumbnail_url = poster?.let(::imageUrl)
    }

    fun toDetails() = toSManga().apply {
        val names = taxonomies.map { it.taxonomy }
            .filterNot { it.name.equals("unknown", ignoreCase = true) }
            .groupBy({ it.type }, { it.name })
            .mapValues { (_, list) -> list.distinctBy { it.lowercase() } }
        author = names["author"]?.joinToString()
        artist = names["artist"]?.joinToString()
        genre = (listOf(type) + names["genre"].orEmpty()).joinToString()
        description = MarkdownDescription.Builder()
            .description(synopsis)
            .field("Alternative titles", alter)
            .build()
        status = when (this@Series.status) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapters() = chapters.map { it.toSChapter(path) }
}

@Serializable
class Chapter(
    private val chapter: Double,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(seriesPath: String) = SChapter.create().apply {
        val number = chapter.toString().removeSuffix(".0")
        url = "${seriesPath}chapter-$number/"
        name = "Chapter $number"
        chapter_number = chapter.toFloat()
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class TaxonomyRelation(val taxonomy: Taxonomy)

@Serializable
class Taxonomy(val name: String, val type: String)

@Serializable
class ChapterRoute(private val data: ChapterData) {
    fun pages() = data.chapters.first().images.entries
        .sortedBy { it.key.toInt() }
        .map { imageUrl(it.value.src) }
}

@Serializable
class ChapterData(val chapters: List<ChapterImages>)

@Serializable
class ChapterImages(val images: Map<String, Image>)

@Serializable
class Image(val src: String)

private val OWN_PATHS = setOf("manhwa", "manhua", "doujinshi")

// Images from other hosts go through the site's image proxy, as on its pages
private fun imageUrl(url: String): String {
    val secure = url.replaceFirst("http://", "https://")
    return if ("imgkomik.xyz" in secure || "manhwature.com" in secure) secure else secure.replaceFirst("https://", "https://cdn.imgkomik.xyz/")
}
