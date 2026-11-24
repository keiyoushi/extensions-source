package eu.kanade.tachiyomi.extension.ja.musicbookjp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
class ViewerRequestBody(
    private val a001: String,
    @SerialName("contents_id") private val contentsId: String,
)

@Serializable
class ViewerResponse(
    val data: String,
    val sckb: String,
)

@Serializable
class PageData(
    @SerialName("display_setting_info") val displaySettingInfo: List<DisplaySetting>,
)

@Serializable
class DisplaySetting(
    @SerialName("layout_url") val layoutUrl: String,
    @SerialName("d") val config: ScrambleConfig,
)

@Serializable
class ScrambleConfig(
    @SerialName("a") val paramList: List<ScrambleParam>,
)

@Serializable
class ScrambleParam(
    @SerialName("i") val coordinates: String,
    @SerialName("p") val lastPage: Int,
)
