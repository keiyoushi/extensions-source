package eu.kanade.tachiyomi.extension.tr.mangatr

import kotlinx.serialization.Serializable

@Serializable
class ChallengeRequestDto(val challenge: String)

@Serializable
class ReaderGateDto(val k: String)

@Serializable
class ReaderConfigDto(val data: ReaderAttributesDto)

@Serializable
class ReaderAttributesDto(
    val parts: String,
    val order: String,
    val pageIndex: String,
)
