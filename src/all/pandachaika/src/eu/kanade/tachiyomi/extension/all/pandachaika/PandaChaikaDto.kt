package eu.kanade.tachiyomi.extension.all.pandachaika

import kotlinx.serialization.Serializable

@Serializable
class Archive(
    val download: String,
    val filecount: Int,
    val filesize: Double,
    val posted: Long,
    val tags: List<String>,
    val title: String,
    val title_jpn: String?,
    val uploader: String,
)

@Serializable
class LongArchive(
    val thumbnail: String,
    val title: String,
    val url: String,
)

@Serializable
class ArchiveResponse(
    val archives: List<LongArchive>,
    val has_next: Boolean,
)
