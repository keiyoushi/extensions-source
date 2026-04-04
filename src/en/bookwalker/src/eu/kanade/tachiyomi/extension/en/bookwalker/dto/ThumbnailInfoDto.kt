package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class ThumbnailInfoDto(
    // Format: "https://domain/{size}/path.{format}"
    // Size is unrelated to the width/height and instead appears to support
    // any value divisible by 120 up to 1200.
    // Format should be webp.
    @ProtoNumber(1) private val urlFormat: String,
    // width and height seem unused in practice, but are returned
//    @ProtoNumber(2) val width: Int,
//    @ProtoNumber(3) val height: Int,
) {
    /**
     * @param size The vertical height in pixels. Must be divisible by 120 and <=1200.
     */
    fun getImageUrl(size: Int): String = urlFormat
        .replace("{size}", size.toString())
        .replace("{format}", "webp")
}
