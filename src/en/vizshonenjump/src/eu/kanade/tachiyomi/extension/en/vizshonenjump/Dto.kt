package eu.kanade.tachiyomi.extension.en.vizshonenjump

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PageUrlDto(
    val data: Map<Int, String>? = null,
)

@Serializable
class MangaAuthDto(
    val ok: Int = 0,
    @SerialName("archive_info") val archiveInfo: ArchiveInfoDto? = null,
)

@Serializable
class ArchiveInfoDto(
    val ok: Int = 0,
    @SerialName("err") val error: ErrorDto? = null,
)

@Serializable
class ErrorDto(
    val code: Int,
    @SerialName("msg") val message: String? = null,
)
