package keiyoushi.lib.publus

import keiyoushi.utils.parseAs
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

@Serializable
class PublusFragment(
    val file: String,
    val no: Int,
    val ns: Long = 0,
    val ps: Long = 0,
    val rs: Long = 0,
    val bw: Int = 0, // Block Width
    val bh: Int = 0, // Block Height
    val cw: Int = 0, // Content Width
    val ch: Int = 0, // Content Height
    val k1: List<Int> = emptyList(),
    val k2: List<Int> = emptyList(),
    val k3: List<Int> = emptyList(),
    val extra: Map<String, String>? = null,
    val s: Boolean = true,
)

fun String.parseFragmentOrNull(): PublusFragment? = runCatching {
    this.parseAs<PublusFragment>()
}.getOrNull()
