package eu.kanade.tachiyomi.multisrc.madara

import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class GenreRoute(
    val name: String,
    val slug: String,
    val path: String,
)

internal fun JsonElement?.genreRoutes(): List<GenreRoute> = this?.parseAs<List<GenreRoute>>().orEmpty()

internal fun List<GenreRoute>.toGenreJson(): JsonElement = toJsonElement()

@Serializable
internal class ChapterProtectorData(
    @SerialName("ct") val ciphertext: String,
    @SerialName("s") val salt: String,
)
