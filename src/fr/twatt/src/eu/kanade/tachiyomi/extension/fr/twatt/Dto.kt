package eu.kanade.tachiyomi.extension.fr.twatt

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class ProjectsResponse(
    val projects: List<Project>,
)

@Serializable
class Project(
    val id: String,
    val title: String,
    val genre: String? = null,
    val type: String? = null,
    val status: String? = null,
    val description: String? = null,
    val coverImage: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/serie/$id"
        title = this@Project.title
        thumbnail_url = coverImage?.let { resolvePath(it, baseUrl) }
        description = this@Project.description
        genre = listOfNotNull(this@Project.genre, this@Project.type).joinToString()
        status = when (this@Project.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

internal fun resolvePath(path: String, baseUrl: String): String = when {
    path.startsWith("http") -> path
    path.startsWith("data:") -> "https://127.0.0.1/?" + path.substringAfter(":")
    else -> "$baseUrl$path"
}

@Serializable
class SeriesResponse(
    val project: Project,
    val chapters: List<ChapterEntry>,
    val mainTeam: Team? = null,
)

@Serializable
class Team(
    val name: String,
)

@Serializable
class ChapterEntry(
    val id: String,
    val number: Int,
    val title: String? = null,
    val accessType: String? = null,
    val releasedAt: String? = null,
)

@Serializable
class ChapterResponse(
    val chapter: ChapterDetail,
)

@Serializable
class ChapterDetail(
    val images: List<String>,
)
