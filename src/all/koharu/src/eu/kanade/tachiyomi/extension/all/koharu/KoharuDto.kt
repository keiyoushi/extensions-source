package eu.kanade.tachiyomi.extension.all.koharu

import eu.kanade.tachiyomi.extension.all.koharu.Koharu.Companion.dateReformat
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
class Tag(
    val name: String,
    val namespace: Int = 0,
)

@Serializable
class Filter(
    private val id: Int,
    private val name: String,
    private val namespace: Int = 0,
) {
    fun toTag() = when (namespace) {
        0 -> KoharuFilters.Genre(id, name)
        1 -> KoharuFilters.Artist(id, name)
        2 -> KoharuFilters.Circle(id, name)
        3 -> KoharuFilters.Parody(id, name)
        8 -> KoharuFilters.Male(id, name)
        9 -> KoharuFilters.Female(id, name)
        10 -> KoharuFilters.Mixed(id, name)
        12 -> KoharuFilters.Other(id, name)
        else -> KoharuFilters.Tag(id, name, namespace)
    }
}

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
    val key: String,
    val title: String,
    val thumbnail: Thumbnail,
)

@Serializable
class MangaDetail(
    val id: Int,
    val title: String,
    val key: String,
    val created_at: Long = 0L,
    val updated_at: Long?,
    val thumbnails: Thumbnails,
    val tags: List<Tag> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
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
        val tags = mutableListOf<String>()
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
                else -> tags.add(tag.name)
            }
        }

        var appended = false
        fun List<String>.joinAndCapitalizeEach(): String? = this.emptyToNull()?.joinToString { it.capitalizeEach() }?.apply { appended = true }

        thumbnail_url = thumbnails.base + thumbnails.main.path

        author = (circles.emptyToNull() ?: artists).joinToString { it.capitalizeEach() }
        artist = artists.joinToString { it.capitalizeEach() }
        genre = (artists + circles + parodies + magazines + characters + cosplayers + tags + females + males + mixed + other).joinToString { it.capitalizeEach() }
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

            try {
                append("Posted: ", dateReformat.format(created_at), "\n")
            } catch (_: Exception) {}

            append("Pages: ", thumbnails.entries.size, "\n\n")
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    private fun String.capitalizeEach() = this.split(" ").joinToString(" ") { s ->
        s.replaceFirstChar { sr ->
            if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
        }
    }

    private fun <T> Collection<T>.emptyToNull(): Collection<T>? {
        return this.ifEmpty { null }
    }
}

@Serializable
class MangaData(
    val data: Data,
    val similar: List<Entry> = emptyList(),
) {
    /**
     * Return human-readable size of chapter.
     * @param quality The quality set in PREF_IMAGERES
     */
    fun size(quality: String): String {
        val dataKey = when (quality) {
            "1600" -> data.`1600` ?: data.`1280` ?: data.`0`
            "1280" -> data.`1280` ?: data.`1600` ?: data.`0`
            "980" -> data.`980` ?: data.`1280` ?: data.`0`
            "780" -> data.`780` ?: data.`980` ?: data.`0`
            else -> data.`0`
        }
        return dataKey.readableSize()
    }
}

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
)

@Serializable
class DataKey(
    val id: Int? = null,
    val size: Double = 0.0,
    val key: String? = null,
) {
    fun readableSize() = when {
        size >= 300 * 1000 * 1000 -> "${"%.2f".format(size / (1000.0 * 1000.0 * 1000.0))} GB"
        size >= 100 * 1000 -> "${"%.2f".format(size / (1000.0 * 1000.0))} MB"
        size >= 1000 -> "${"%.2f".format(size / (1000.0))} kB"
        else -> "$size B"
    }
}

@Serializable
class ImagesInfo(
    val base: String,
    val entries: List<ImagePath>,
)

@Serializable
class ImagePath(
    val path: String,
)
