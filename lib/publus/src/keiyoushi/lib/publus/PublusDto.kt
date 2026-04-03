package keiyoushi.lib.publus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val index: Int,
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
    @SerialName("No") val no: Int = 0,
    @SerialName("NS") val ns: Long = 0L,
    @SerialName("PS") val ps: Long = 0L,
    @SerialName("RS") val rs: Long = 0L,
    @SerialName("BlockWidth") val blockWidth: Int = 0,
    @SerialName("BlockHeight") val blockHeight: Int = 0,
    @SerialName("DummyWidth") val dummyWidth: Int? = null,
    @SerialName("Size") val size: PublusPageSize,
)

@Serializable
class PublusPageSize(
    @SerialName("Width") val width: Int,
    @SerialName("Height") val height: Int,
)
