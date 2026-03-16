package eu.kanade.tachiyomi.extension.ja.comicnettai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CPhpResponse(
    val url: String,
)

@Serializable
class ConfigPack(
    val data: String,
)

@Serializable
class PublusConfiguration(
    val contents: List<PublusContentEntry>,
)

@Serializable
class PublusContentEntry(
    val file: String,
)

@Serializable
class PublusPageConfig(
    @SerialName("FileLinkInfo") val fileLinkInfo: PublusFileLinkInfo,
)

@Serializable
class PublusFileLinkInfo(
    @SerialName("PageLinkInfoList") val pageLinkInfoList: List<PublusPageLinkInfoWrapper>,
)

@Serializable
class PublusPageLinkInfoWrapper(
    @SerialName("Page") val page: PublusPageDetails,
)

@Serializable
class PublusPageDetails(
    @SerialName("No") val no: Int,
    @SerialName("NS") val ns: Long,
    @SerialName("PS") val ps: Long,
    @SerialName("RS") val rs: Long,
    @SerialName("BlockWidth") val blockWidth: Int,
    @SerialName("BlockHeight") val blockHeight: Int,
    @SerialName("Size") val size: PublusPageSize,
)

@Serializable
class PublusPageSize(
    @SerialName("Width") val width: Int,
    @SerialName("Height") val height: Int,
)
