package eu.kanade.tachiyomi.extension.all.lanraragi

import kotlinx.serialization.Serializable

@Serializable
class Archive(
    val arcid: String,
    val title: String,
    val tags: String?,
    val summary: String?,
    val isnew: Boolean,
    val pagecount: Int,
    val toc: List<ArchiveTOCEntry>?,
)

@Serializable
class ArchiveTOCEntry(
    val name: String,
    val page: Int,
)

@Serializable
class ArchivePage(
    val pages: List<String>,
)

@Serializable
class ArchiveSearchResult(
    val data: List<Archive>,
    val recordsFiltered: Int?,
    val recordsTotal: Int,
)

@Serializable
class Category(
    val id: String,
    val name: String?,
    val pinned: Int?,
)

@Serializable
class Tankoubon(
    val result: TankoubonMetadataJson?,
    val total: Int?,
    val filtered: Int?,
)

@Serializable
class TankoubonMetadataJson(
    val id: String,
    val name: String?,
    val summary: String?,
    val tags: String?,
    val archives: List<String>?,
    val full_data: List<Archive>?,
)
