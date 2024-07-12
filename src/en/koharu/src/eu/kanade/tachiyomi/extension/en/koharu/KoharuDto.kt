package eu.kanade.tachiyomi.extension.en.koharu

import kotlinx.serialization.Serializable

@Serializable
class Tag(
    var name: String,
    var namespace: Int = 0,
)

@Serializable
class Books(
    val entries: List<Entry> = emptyList(),
    val total: Int,
    val page: Int,
    val limit: Int,
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
    val public_key: String,
    val created_at: Long = 0L,
    val updated_at: Long?,
    val thumbnails: Thumbnails,
    val tags: List<Tag> = emptyList(),
    val data: Data,
)

@Serializable
class Thumbnails(
    val entries: List<Thumbnail>,
)

@Serializable
class Thumbnail(
    val path: String,
)

@Serializable
class Data(
    val `0`: DataKey,
    val `780`: DataKey = `0`,
    val `980`: DataKey = `780`,
    val `1280`: DataKey = `980`,
    val `1600`: DataKey = `1280`,
)

@Serializable
class DataKey(
    val id: Int,
    val public_key: String,
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
