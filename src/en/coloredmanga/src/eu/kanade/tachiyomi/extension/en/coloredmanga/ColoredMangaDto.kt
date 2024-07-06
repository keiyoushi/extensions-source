package eu.kanade.tachiyomi.extension.en.coloredmanga

import kotlinx.serialization.Serializable

@Serializable
class Mangas(
    val id: String,
    val name: String,
    val date: String,
    val tags: List<String>,
    val volume: List<Volume> = listOf(),
    val chapters: List<Chapter> = listOf(),
    val totalViews: Int,
    val synopsis: String,
    val author: String,
    val artist: String,
    val cover: String,
    val status: String,
    val version: String,
    val type: String,
)

@Serializable
class MangasList(
    val data: List<Mangas>,
)

@Serializable
class Volume(
    val id: String,
    val title: String = "",
    val number: String,
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    val id: String,
    val title: String = "",
    val number: String,
    val date: String,
    val totalImage: Int,
)
