package eu.kanade.tachiyomi.extension.en.doujins

import kotlinx.serialization.Serializable

@Serializable
class FoldersDto(
    val folders: List<FolderDto>,
)

@Serializable
class FolderDto(
    val link: String,
    val name: String,
    val artistList: String,
    val tags: List<TagDto>,
    val thumbnail2: String,
)

@Serializable
class TagDto(
    val tag: String,
)
