package eu.kanade.tachiyomi.multisrc.scanr

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
import kotlinx.serialization.json.longOrNull

object SafeLongDeserializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SafeLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return try {
            decoder.decodeLong()
        } catch (_: Exception) {
            0L
        }

        return try {
            val element = jsonDecoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> {
                    element.longOrNull ?: element.content.toLongOrNull() ?: 0L
                }

                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}

@Serializable
data class ConfigResponse(
    @SerialName("LOCAL_SERIES_FILES")
    val localSeriesFiles: List<String>,
)

@Serializable
data class SeriesData(
    val title: String,
    val description: String?,
    val artist: String?,
    val author: String?,
    val cover: String?,
    @SerialName("cover_low")
    val coverLow: String?,
    @SerialName("cover_hq")
    val coverHq: String?,
    val tags: List<String>?,
    @SerialName("release_status")
    val releaseStatus: String?,
    @SerialName("alternative_titles")
    val alternativeTitles: List<String>?,
    val chapters: Map<String, ChapterData>?,
)

@Serializable
data class ReaderData(
    val series: SeriesData,
)

@Serializable
data class ChapterData(
    val title: String?,
    val volume: String?,
    @SerialName("last_updated")
    @Serializable(with = SafeLongDeserializer::class)
    val lastUpdated: Long = 0L,
    val licencied: Boolean = false,
    val groups: Map<String, String>?,
)

@Serializable
data class PageData(
    val link: String,
)

// DTO to SManga extension functions
fun SeriesData.toSManga(useLowQuality: Boolean = false, slugSeparator: String): SManga =
    SManga.create().apply {
        title = this@toSManga.title
        artist = this@toSManga.artist
        author = this@toSManga.author
        thumbnail_url = if (useLowQuality) this@toSManga.coverHq else this@toSManga.cover
        url = "/${toSlug(this@toSManga.title, slugSeparator)}"
    }

fun SeriesData.toDetailedSManga(useHighQuality: Boolean = false, slugSeparator: String): SManga =
    SManga.create().apply {
        title = this@toDetailedSManga.title

        val baseDescription = this@toDetailedSManga.description.let {
            if (it?.contains("Pas de synopsis", ignoreCase = true) == true) null else it
        }

        val altTitles = this@toDetailedSManga.alternativeTitles
        description = if (!altTitles.isNullOrEmpty()) {
            buildString {
                if (!baseDescription.isNullOrBlank()) {
                    append(baseDescription)
                    append("\n\n")
                }
                append("Alternative Titles:\n")
                append(altTitles.joinToString("\n") { "• $it" })
            }
        } else {
            baseDescription
        }

        artist = this@toDetailedSManga.artist
        author = this@toDetailedSManga.author
        genre = this@toDetailedSManga.tags?.joinToString(", ") ?: ""
        status = when (this@toDetailedSManga.releaseStatus) {
            "En cours" -> SManga.ONGOING
            "Finis", "Fini" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url =
            if (useHighQuality) this@toDetailedSManga.coverHq else this@toDetailedSManga.cover
        url = "/${toSlug(this@toDetailedSManga.title, slugSeparator)}"
    }

// Utility function for slug generation
// URLs are manually calculated using a slugify function
fun toSlug(input: String?, slugSeparator: String = "-"): String {
    if (input == null) return ""

    val accentsMap = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n',
    )

    return input
        .lowercase()
        .map { accentsMap[it] ?: it }
        .joinToString("")
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .replace("\\s".toRegex(), slugSeparator)
}
