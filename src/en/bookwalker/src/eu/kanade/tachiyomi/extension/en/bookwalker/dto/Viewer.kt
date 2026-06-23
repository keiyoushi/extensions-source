package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Suppress("unused")
@Serializable
class ViewerRequestBody(
    @ProtoNumber(1)
    @SerialName("product_id")
    val productId: String,
)

@Serializable
class ViewerResponse(
    @ProtoNumber(2) val details: ViewerDetails,
)

@Serializable
class ViewerDetails(
    @ProtoNumber(1) val manifestUrl: String,
    @ProtoNumber(6) val mimeType: String,
)
