package eu.kanade.tachiyomi.extension.zh.guazimanhua

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AppTokenResponse(
    @SerialName("error_code")
    val errorCode: Int = 0,
    val data: AppTokenData? = null,
)

@Serializable
class AppTokenData(
    val token: String? = null,
)

@Serializable
class AppPicsResponse(
    @SerialName("error_code")
    val errorCode: Int = 0,
    val data: AppPicsData? = null,
)

@Serializable
class AppPicsData(
    val images: List<AppImage> = emptyList(),
)

@Serializable
class AppImage(
    // base64 of AES/CBC ciphertext, decrypts to the absolute image URL
    val img: String,
)
