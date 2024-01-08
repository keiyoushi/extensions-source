package eu.kanade.tachiyomi.extension.fr.fmteam

import kotlinx.serialization.Serializable

@Serializable
data class FmteamComicListPage(
    val comics: List<FmteamComic>,
)

@Serializable
data class FmteamComicDetailPage(
    val comic: FmteamComic,
)

@Serializable
data class FmteamChapterDetailPage(
    val chapter: FmteamChapter,
)

@Serializable
data class FmteamComic(
    val title: String,
    val thumbnail: String,
    val description: String,
    val author: String,
    val artist: String?,
    val genres: List<FmteamTag>,
    val status: String,
    val views: Int,
    val url: String,
    val last_chapter: FmteamChapter,
    val chapters: List<FmteamChapter>?,
)

@Serializable
data class FmteamTag(
    val name: String,
)

@Serializable
data class FmteamChapter(
    val full_title: String,
    val chapter: Int,
    val subchapter: Int?,
    val teams: List<FmteamTeam?>,
    val published_on: String,
    val url: String,
    val pages: List<String>?,
)

@Serializable
data class FmteamTeam(
    val name: String,
)
