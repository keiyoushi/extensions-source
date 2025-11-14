package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ViewerAuthResponse(
    val url: String,
    val cty: Int,
    @SerialName("auth_info") val authInfo: AuthInfo,
)

@Serializable
class AuthInfo(
    @SerialName("Key-Pair-Id") val keyPairId: String,
    @SerialName("Policy") val policy: String,
    @SerialName("Signature") val signature: String,
)

@Serializable
class ViewerConfiguration(
    val contents: List<Content>,
)

@Serializable
class Content(
    val file: String,
    val type: String,
)

@Serializable
class ViewerPageInfo(
    @SerialName("FileLinkInfo") val fileLinkInfo: FileLinkInfo,
)

@Serializable
class FileLinkInfo(
    @SerialName("PageLinkInfoList") val pageLinkInfoList: List<PageLinkWrapper>,
)

@Serializable
class PageLinkWrapper(
    @SerialName("Page") val page: PageData,
)

@Serializable
class PageData(
    @SerialName("No") val pageNumber: Int,
)
