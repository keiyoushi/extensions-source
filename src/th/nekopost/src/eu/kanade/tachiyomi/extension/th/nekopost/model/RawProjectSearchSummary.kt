package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
data class RawProjectSearchSummary(
    val pid: Int,
    val projectName: String,
    val aliasName: String = "",
    val website: String = "",
    val authorId: Int = 0,
    val authorName: String = "",
    val artistId: Int = 0,
    val artistName: String = "",
    val info: String = "",
    val status: Int = 0,
    val flgMature: String = "",
    val flgIntense: String = "",
    val flgViolent: String = "",
    val flgGlue: String = "",
    val flgReligion: String = "",
    val flgHidemeta: String = "",
    val mainCategory: String = "",
    val goingType: String = "",
    val projectType: String = "",
    val readerGroup: String = "",
    val releaseDate: ProjectDate = ProjectDate(),
    val updateDate: ProjectDate = ProjectDate(),
    val views: Int = 0,
    val imageVersion: Int = 0,
    val noChapter: Int = 0,
    val coverVersion: Int = 0,
    val concatCate: String = "",
    val editorId: Int = 0,
    val editorName: String = "",
)

@Serializable
data class ProjectDate(
    val String: String = "",
    val Valid: Boolean = false,
)
