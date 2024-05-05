package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
data class FirebaseAuthDto(
    val idToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

@Serializable
data class FirebaseRegisterDto(
    val localId: String,
    val idToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
