package eu.kanade.tachiyomi.extension.ar.peachbl

import kotlinx.serialization.Serializable

@Serializable
class ReaderConfig(val ajax: String, val cid: Long)

@Serializable
class AjaxResponse<T>(val success: Boolean, val data: T)

@Serializable
class TokenDto(val t: String, val exp: Long)

@Serializable
class ImagesDto(
    val urls: List<String> = emptyList(),
    val code: String? = null,
    val message: String? = null,
)
