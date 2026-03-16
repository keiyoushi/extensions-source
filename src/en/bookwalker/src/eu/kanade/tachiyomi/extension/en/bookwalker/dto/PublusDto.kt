package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CPhpResponse(
    val url: String,
    val cty: Int,
    @SerialName("auth_info") val authInfo: AuthInfo,
)

@Serializable
class AuthInfo(
    val hti: String?,
    val cfg: Int?,
    val uuid: String?,
    val pfCd: String,
    @SerialName("Policy") val policy: String,
    @SerialName("Signature") val signature: String,
    @SerialName("Key-Pair-Id") val keyPairId: String,
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
    val index: Int,
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
    @SerialName("Size") val size: PublusPageSize,
)

@Serializable
class PublusPageSize(
    @SerialName("Width") val width: Int,
    @SerialName("Height") val height: Int,
)
