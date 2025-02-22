package eu.kanade.tachiyomi.extension.en.mangamo.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

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
