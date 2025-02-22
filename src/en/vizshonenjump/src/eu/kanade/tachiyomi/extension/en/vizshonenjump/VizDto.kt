package eu.kanade.tachiyomi.extension.en.vizshonenjump
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VizPageUrlDto(
    val ok: Int = 0,
    val data: Map<Int, String>? = null,
)

@Serializable
data class VizMangaAuthDto(
    val ok: Int = 0,
    @SerialName("archive_info") val archiveInfo: VizArchiveInfoDto? = null,
)

@Serializable
data class VizArchiveInfoDto(
    val ok: Int = 0,
    @SerialName("err") val error: VizErrorDto? = null,
)

@Serializable
data class VizErrorDto(
    val code: Int,
    @SerialName("msg") val message: String? = null,
)
