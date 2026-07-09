package eu.kanade.tachiyomi.extension.vi.hentaicube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChallengeResponse(
    val token: String,
    val session: String,
)

@Serializable
class PagesResponse(
    val items: List<String>,
    val done: Boolean,
    @SerialName("next_token") val nextToken: String? = null,
    val session: String? = null,
    @SerialName("protocol_policy") val protocolPolicy: ProtocolPolicy? = null,
)

@Serializable
class ProtocolPolicy(
    val action: String = "continue",
    val transaction: String? = null,
)
