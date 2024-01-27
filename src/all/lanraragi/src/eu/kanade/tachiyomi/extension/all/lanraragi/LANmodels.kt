package eu.kanade.tachiyomi.extension.all.lanraragi

import kotlinx.serialization.Serializable

@Serializable
data class Archive(
    val arcid: String,
    val isnew: String,
    val tags: String?,
    val title: String,
)

@Serializable
data class ArchivePage(
    val pages: List<String>,
)

@Serializable
data class ArchiveSearchResult(
    val data: List<Archive>,
    val recordsFiltered: Int,
    val recordsTotal: Int,
)

@Serializable
data class Category(
    val id: String,
    val last_used: String,
    val name: String,
    val pinned: String,
)
