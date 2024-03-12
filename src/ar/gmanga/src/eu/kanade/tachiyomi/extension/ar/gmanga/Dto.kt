package eu.kanade.tachiyomi.extension.ar.gmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class ChapterListResponse(
    val releases: List<ChapterRelease>,
    val chapterizations: List<Chapterization>,
    val teams: List<Team>,
)

@Serializable
class ChapterRelease(
    val id: Int,
    @SerialName("chapterization_id") val chapId: Int,
    @SerialName("team_id") val teamId: Int,
    val chapter: JsonPrimitive,
    @SerialName("time_stamp") val timestamp: Long,
)

@Serializable
class Chapterization(
    val id: Int,
    val title: String,
)

@Serializable
class Team(
    val id: Int,
    val name: String,
)
