package eu.kanade.tachiyomi.extension.all.lanraragi

import kotlinx.serialization.Serializable

@Serializable
data class Archive(
    val arcid: String,
    val title: String,
    val tags: String?,
    val summary: String?,
    val isnew: Boolean,
    val pagecount: Int,
    val toc: List<ArchiveTOCEntry>?,
)

@Serializable
data class ArchiveTOCEntry(
    val name: String,
    val page: Int,
)

@Serializable
data class ArchivePage(
    val pages: List<String>,
)

@Serializable
data class ArchiveSearchResult(
    val data: List<Archive>,
    val recordsFiltered: Int?,
    val recordsTotal: Int,
)

@Serializable
data class Category(
    val id: String,
    val name: String?,
    val pinned: Int?,
)

@Serializable
data class Tankoubon(
    val result: TankoubonMetadataJson?,
    val total: Int?,
    val filtered: Int?,
)

@Serializable
data class TankoubonMetadataJson(
    val id: String,
    val name: String?,
    val summary: String?,
    val tags: String?,
    val archives: List<String>?,
    val full_data: List<Archive>?,
)
