package eu.kanade.tachiyomi.multisrc.zingtheme

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody

internal fun imageContentType(response: Response, url: HttpUrl = response.request.url): Response {
    if (response.header("Content-Type")?.contains("application/octet-stream", ignoreCase = true) != true) return response
    val extension = url.encodedPath.substringAfterLast('.', "").lowercase()
    val mediaType = IMAGE_TYPES[extension] ?: return response
    return response.newBuilder()
        .header("Content-Type", mediaType)
        .body(response.body.source().asResponseBody(mediaType.toMediaType()))
        .build()
}

private val IMAGE_TYPES = mapOf(
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "webp" to "image/webp",
    "gif" to "image/gif",
    "avif" to "image/avif",
)
