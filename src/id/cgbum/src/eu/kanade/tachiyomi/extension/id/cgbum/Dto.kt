package eu.kanade.tachiyomi.extension.id.cgbum

import kotlinx.serialization.Serializable

@Serializable
class ReaderAccessDto(
    val endpoint: String,
    val page: String,
)

@Serializable
class ReaderAccessRequest(
    private val sid: String,
    private val page: String,
    private val token: String,
    private val ticket: String? = null,
    private val nonce: String? = null,
)

@Serializable
class ReaderAccessResponse(
    val images: List<String>? = null,
    val challenge: ChallengeDto? = null,
    val grant: GrantDto? = null,
)

@Serializable
class ChallengeDto(
    val seed: String,
    val difficulty: Int,
    val ticket: String,
)

@Serializable
class GrantDto(
    val token: String,
)
