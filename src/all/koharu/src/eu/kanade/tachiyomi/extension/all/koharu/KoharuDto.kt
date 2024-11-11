package eu.kanade.tachiyomi.extension.all.koharu

import kotlinx.serialization.Serializable

@Serializable
class Tag(
    var name: String,
    var namespace: Int = 0,
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
    val public_key: String,
    val title: String,
    val thumbnail: Thumbnail,
)

@Serializable
class MangaEntry(
    val id: Int,
    val title: String,
    val public_key: String,
    val created_at: Long = 0L,
    val updated_at: Long?,
    val thumbnails: Thumbnails,
    val tags: List<Tag> = emptyList(),
    val data: Data,
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
)

@Serializable
class DataKey(
    val id: Int? = null,
    val size: Double = 0.0,
    val public_key: String? = null,
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
