package eu.kanade.tachiyomi.extension.fr.manganova

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

/**
 * Data Transfer Objects for MangaNova extension
 */

object SafeFloatDeserializer : KSerializer<Float> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SafeFloat", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: Float) {
        encoder.encodeFloat(value)
    }

    override fun deserialize(decoder: Decoder): Float {
        val jsonDecoder = decoder as? JsonDecoder ?: return try {
            decoder.decodeFloat()
        } catch (_: Exception) {
            -1F
        }

        return try {
            val element = jsonDecoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> {
                    element.floatOrNull ?: element.content.toFloatOrNull() ?: -1F
                }

                else -> -1F
            }
        } catch (_: Exception) {
            -1F
        }
    }
}

@Serializable
class Catalogue(
    val series: List<Serie>,
    @SerialName("new_series")
    val newSeries: List<Serie>,
)

@Serializable
class Serie(
    val title: String,
    @SerialName("title_jap")
    val titleJap: String,
    val slug: String,
    val description: String,
    val genres: String,
    val poster: String,
    val author: String,
    val dessinateur: String,
    val running: Int,
)

@Serializable
class DetailedSerieContainer(
    val serie: DetailedSerie,
)

@Serializable
class DetailedSerie(
    val slug: String,
    val chapitres: List<Category>,
)

@Serializable
class Category(
    val title: String,
    val chapitres: List<Chapter>,
)

@Serializable
class Chapter(
    val title: String,
    @SerialName("sub_title")
    val subTitle: String,
    @Serializable(with = SafeFloatDeserializer::class)
    val number: Float,
    @SerialName("available_time")
    val availableTime: Long,
    val amount: Int,
)

@Serializable
class ChapterDetails(
    val images: List<Image>,
)

@Serializable
class Image(
    val image: String,
    @SerialName("page_number")
    val pageNumber: Int,
)

// DTO to SManga extension functions
fun Serie.toDetailedSManga(): SManga = SManga.create().apply {
    title = this@toDetailedSManga.title
    description = this@toDetailedSManga.description
    artist = this@toDetailedSManga.dessinateur
    author = this@toDetailedSManga.author
    status = if (this@toDetailedSManga.running == 0) SManga.COMPLETED else SManga.ONGOING
    thumbnail_url = this@toDetailedSManga.poster
    url = "/manga/${this@toDetailedSManga.slug}"
    genre = this@toDetailedSManga.genres.replace(",", ", ")
}
