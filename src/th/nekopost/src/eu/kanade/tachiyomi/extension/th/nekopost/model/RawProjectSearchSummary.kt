package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProjectSearchSummary(
    val pid: Int,
    val projectName: String,
    val aliasName: String,
    val website: String,
    val authorId: Int,
    val authorName: String,
    val artistId: Int,
    val artistName: String,
    val info: String,
    val status: Int,
    val flgMature: String,
    val flgIntense: String,
    val flgViolent: String,
    val flgGlue: String,
    val flgReligion: String,
    val flgHidemeta: String,
    val mainCategory: String,
    val goingType: String,
    val projectType: String,
    val readerGroup: String,
    val releaseDate: ProjectDate = ProjectDate(),
    val updateDate: ProjectDate = ProjectDate(),
    val views: Int,
    val imageVersion: Int,
    val noChapter: Int,
    val coverVersion: Int,
    val concatCate: String,
    val editorId: Int,
    val editorName: String,
)

@Serializable
data class ProjectDate(
    @SerialName("String") val string: String = "",
    @SerialName("Valid") val valid: Boolean = false,
)
