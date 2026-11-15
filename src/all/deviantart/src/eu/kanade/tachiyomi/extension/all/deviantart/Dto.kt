package eu.kanade.tachiyomi.extension.all.deviantart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class InitialState(@SerialName("@@entities") val entities: Entities)

@Serializable
class Entities(val deviationExtended: Map<String, ExtendedDeviation>)

@Serializable
class ExtendedDeviation(val additionalMedia: List<AdditionalMedia> = emptyList())

@Serializable
class AdditionalMedia(val media: Media)

@Serializable
class Media(
    private val baseUri: String,
    private val prettyName: String,
    private val types: List<MediaType>,
    private val token: List<String> = emptyList(),
) {
    fun imageUrl(): String {
        val fullview = types.first { it.t == "fullview" }
        // A token may only authorize the site's resized image, not the original file.
        val path = fullview.c.orEmpty().replace("<prettyName>", prettyName)
        return (baseUri + path).toHttpUrl().newBuilder().apply {
            token.getOrNull(fullview.r)?.let { addQueryParameter("token", it) }
        }.build().toString()
    }
}

@Serializable
class MediaType(val t: String, val c: String? = null, val r: Int = 0)
