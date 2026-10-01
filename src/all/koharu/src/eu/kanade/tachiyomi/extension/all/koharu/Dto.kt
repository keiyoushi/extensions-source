package eu.kanade.tachiyomi.extension.all.koharu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.time.Instant

@Serializable
class Tag(
    val name: String,
    val namespace: Int = 0,
)

@Serializable
class FilterDto(
    val id: Int,
    val name: String,
    val namespace: Int = 0,
)

@Serializable
class Books(
    val entries: List<Entry> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val page: Int,
)

@Serializable
class Entry(
    val id: Int,
    private val key: String,
    val title: String,
    private val thumbnail: Thumbnail,
) {
    fun toSManga(removeAddInfo: Boolean = false) = SManga.create().apply {
        url = "${this@Entry.id}/$key"
        title = if (removeAddInfo) this@Entry.title.shortenTitle() else this@Entry.title
        thumbnail_url = thumbnail.path
    }
}

private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
fun String.shortenTitle() = replace(shortenTitleRegex, "").trim()

@Serializable
class MangaDetail(
    private val id: Int,
    private val title: String,
    private val key: String,
    @SerialName("created_at") private val createdAt: Long = 0L,
    @SerialName("updated_at") private val updatedAt: Long? = null,
    private val thumbnails: Thumbnails,
    private val tags: List<Tag> = emptyList(),
) {
    fun toSManga(removeAddInfo: Boolean = false) = SManga.create().apply {
        val artists = mutableListOf<String>()
        val circles = mutableListOf<String>()
        val parodies = mutableListOf<String>()
        val magazines = mutableListOf<String>()
        val characters = mutableListOf<String>()
        val cosplayers = mutableListOf<String>()
        val females = mutableListOf<String>()
        val males = mutableListOf<String>()
        val mixed = mutableListOf<String>()
        val language = mutableListOf<String>()
        val other = mutableListOf<String>()
        val uploaders = mutableListOf<String>()
        val generalTags = mutableListOf<String>()
        this@MangaDetail.tags.forEach { tag ->
            when (tag.namespace) {
                1 -> artists.add(tag.name)
                2 -> circles.add(tag.name)
                3 -> parodies.add(tag.name)
                4 -> magazines.add(tag.name)
                5 -> characters.add(tag.name)
                6 -> cosplayers.add(tag.name)
                7 -> tag.name.takeIf { it != "anonymous" }?.let { uploaders.add(it) }
                8 -> males.add(tag.name + " ♂")
                9 -> females.add(tag.name + " ♀")
                10 -> mixed.add(tag.name)
                11 -> language.add(tag.name)
                12 -> other.add(tag.name)
                else -> generalTags.add(tag.name)
            }
        }

        var appended = false
        fun List<String>.joinAndCapitalizeEach(): String? = this.emptyToNull()?.joinToString { it.capitalizeEach() }?.apply { appended = true }

        url = "$id/$key"
        title = if (removeAddInfo) this@MangaDetail.title.shortenTitle() else this@MangaDetail.title
        thumbnail_url = thumbnails.base + thumbnails.main.path

        author = (circles.emptyToNull() ?: artists).joinToString { it.capitalizeEach() }
        artist = artists.joinToString { it.capitalizeEach() }
        genre = (artists + circles + parodies + magazines + characters + cosplayers + generalTags + females + males + mixed + other).joinToString { it.capitalizeEach() }
        description = buildString {
            circles.joinAndCapitalizeEach()?.let {
                append("Circles: ", it, "\n")
            }
            uploaders.joinAndCapitalizeEach()?.let {
                append("Uploaders: ", it, "\n")
            }
            magazines.joinAndCapitalizeEach()?.let {
                append("Magazines: ", it, "\n")
            }
            cosplayers.joinAndCapitalizeEach()?.let {
                append("Cosplayers: ", it, "\n")
            }
            parodies.joinAndCapitalizeEach()?.let {
                append("Parodies: ", it, "\n")
            }
            characters.joinAndCapitalizeEach()?.let {
                append("Characters: ", it, "\n")
            }

            if (appended) append("\n")

            append("Posted: ", Instant.fromEpochMilliseconds(createdAt).toString(), "\n")

            append("Pages: ", thumbnails.entries.size, "\n\n")
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    fun toSChapter() = SChapter.create().apply {
        name = "Chapter"
        url = "$id/$key"
        date_upload = updatedAt ?: createdAt
    }

    private fun String.capitalizeEach() = this.split(" ").joinToString(" ") { s ->
        s.replaceFirstChar { sr ->
            if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
        }
    }

    private fun <T> Collection<T>.emptyToNull(): Collection<T>? = this.ifEmpty { null }
}

@Serializable
class MangaData(
    val data: Data,
    val similar: List<Entry> = emptyList(),
)

@Serializable
class Thumbnails(
    val base: String,
    val main: Thumbnail,
    val entries: List<Thumbnail>,
)

@Serializable
class Thumbnail(
    val path: String,
)

@Serializable
class Data(
    val `0`: DataKey,
    val `780`: DataKey? = null,
    val `980`: DataKey? = null,
    val `1280`: DataKey? = null,
    val `1600`: DataKey? = null,
) {
    fun getBestQuality(preferred: String): SelectedQuality? {
        val priorities = qualityPriorities[preferred] ?: defaultPriority
        return priorities.firstNotNullOfOrNull { quality ->
            val key = when (quality) {
                "1600" -> `1600`
                "1280" -> `1280`
                "980" -> `980`
                "780" -> `780`
                "0" -> `0`
                else -> null
            }
            if (key?.id != null && key.key != null) {
                SelectedQuality(quality, key.id, key.key)
            } else {
                null
            }
        }
    }
}

class SelectedQuality(val quality: String, val id: Int, val key: String)

private val defaultPriority = listOf("1280", "1600", "980", "780", "0")
private val qualityPriorities = mapOf(
    "1600" to listOf("1600", "1280", "980", "780", "0"),
    "1280" to listOf("1280", "1600", "980", "780", "0"),
    "980" to listOf("980", "1280", "1600", "780", "0"),
    "780" to listOf("780", "980", "1280", "1600", "0"),
    "0" to listOf("0", "1280", "1600", "980", "780"),
)

@Serializable
class DataKey(
    val id: Int? = null,
    val key: String? = null,
)

@Serializable
class ImagesInfo(
    val base: String,
    val entries: List<ImagePath>,
)

@Serializable
class ImagePath(
    val path: String,
)
