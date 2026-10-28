package eu.kanade.tachiyomi.extension.zh.baozimanhua

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChallengeRequired(
    val error: String? = null,
    @SerialName("challenge_url") val challengeUrl: String? = null,
)

@Serializable
class VerifyRequest(
    @SerialName("challenge_id") private val challengeId: String,
    private val ticket: String,
    private val nonce: String,
)

@Serializable
class VerifyResponse(
    val status: String,
)
