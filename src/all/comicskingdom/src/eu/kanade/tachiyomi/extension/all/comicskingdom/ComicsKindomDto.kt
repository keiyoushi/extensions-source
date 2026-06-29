package eu.kanade.tachiyomi.extension.all.comicskingdom

import kotlinx.serialization.Serializable

@Serializable
class Chapter(
    val id: Int,
    val date: String,
    val assets: Assets?,
    val link: String,
)

@Serializable
class Assets(
    val single: AssetData,
)

@Serializable
class AssetData(
    val url: String,
)

@Serializable
class Manga(
    val id: Int,
    val link: String,
    val title: Rendered,
    val content: Rendered,
    val meta: MangaMeta,
    val yoast_head: String,
)

@Serializable
class MangaMeta(
    val ck_byline_on_app: String,
)

@Serializable
class Rendered(
    val rendered: String,
)

val ChapterFields = Chapter.javaClass.fields.joinToString(",") { it.name }
val MangaFields = Manga.javaClass.fields.joinToString(",") { it.name }
