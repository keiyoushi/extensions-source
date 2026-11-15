package eu.kanade.tachiyomi.extension.en.sunshinebutterflyscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EntryDto(
    val series: String,
    val timestamp: String,
    val num: Int,
    @SerialName("chname") val chapterName: String,
    @SerialName("AlbumID") val albumID: String,
    @SerialName("projectname") val projectName: String,
    @SerialName("projectdesc") val projectDesc: String,
    @SerialName("projectaltname") val altName: String,
    @SerialName("projectauthor") val projectAuthor: String,
    @SerialName("projectartist") val projectArtist: String,
    @SerialName("projectthumb") val projectThumb: String,
    @SerialName("projectstatus") val projectStatus: String,
    @SerialName("projecttags") val projectTags: String,
) {
    fun toSManga(cdnUrl: String): SManga = SManga.create().apply {
        title = series
        thumbnail_url = cdnUrl + projectThumb
        url = "/projects?n=$projectName"
        description = buildString {
            projectDesc.nonEmpty()?.let { append(it) }
            if (altName.isNotEmpty()) {
                append("\n\n")
                append("Alternative name: ")
                append(altName)
            }
        }
        genre = projectTags.nonEmpty()?.replace(",", ", ")
        status = projectStatus.toStatus()
        author = projectAuthor.nonEmpty()
        artist = projectArtist.nonEmpty()
        initialized = true
    }

    private fun String.toStatus(): Int = when (this) {
        "current" -> SManga.ONGOING
        "complete" -> SManga.COMPLETED
        "dropped" -> SManga.CANCELLED
        "licensed" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    fun toSChapter(): SChapter = SChapter.create().apply {
        name = chapterName
        chapter_number = num.toFloat()
        date_upload = timestamp.nonEmpty()?.toLong()?.times(1000) ?: 0L
        url = "/read?series=$projectName&num=$num"
    }
}

@Serializable
class GoogleDriveResponseDto(
    val files: List<FileDto>,
) {
    @Serializable
    class FileDto(
        val id: String,
        val name: String,
        @SerialName("imageMediaMetadata") val metadata: MetadataDto,
    ) {
        @Serializable
        class MetadataDto(
            val width: Int,
        )
    }
}

@Serializable
class ImgurResponseDto(
    val data: List<DataDto>,
) {
    @Serializable
    class DataDto(
        val link: String,
    )
}

private fun String.nonEmpty() = this.takeIf { it.isNotEmpty() }
