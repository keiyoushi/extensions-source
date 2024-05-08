package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
class FirebaseAuthDto(
    val idToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

@Serializable
class FirebaseRegisterDto(
    val localId: String,
    val idToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
