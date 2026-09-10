package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class LunarSearchResponse(
    val manga: List<LunarMangaDto> = emptyList(),
    val page: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
)

@Serializable
class LunarMangaResponse(
    val manga: LunarMangaDto,
)

@Serializable
class LunarMangaDto(
    val slug: String,
    val title: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val genres: String? = null,
    @SerialName("publication_status") val publicationStatus: String? = null,
    val author: String? = null,
    val artist: String? = null,
    @SerialName("alternative_titles") val alternativeTitles: String? = null,
    val demographic: String? = null,
    val themes: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@LunarMangaDto.title
        thumbnail_url = coverUrl
        url = "/manga/$slug"
        author = this@LunarMangaDto.author?.trim()
        artist = this@LunarMangaDto.artist?.trim()

        description = buildString {
            this@LunarMangaDto.description?.let { append(it) }

            alternativeTitles?.let { alt ->
                try {
                    val titles = alt.parseAs<List<String>>()
                    if (titles.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Alternative Titles: ")
                        append(titles.joinToString())
                    }
                } catch (_: Exception) {}
            }
        }

        status = when (publicationStatus?.lowercase(Locale.ROOT)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "upcoming" -> SManga.ONGOING
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        genre = buildList {
            demographic?.takeIf { it.isNotBlank() }?.let { d ->
                add(d.replaceFirstChar { it.titlecase(Locale.ROOT) })
            }

            genres?.let { g ->
                try {
                    addAll(g.parseAs<List<String>>())
                } catch (e: Exception) {
                    add(g)
                }
            }

            themes?.let { t ->
                try {
                    addAll(t.parseAs<List<String>>())
                } catch (_: Exception) {}
            }
        }.filter { it.isNotBlank() }.distinct().joinToString()
    }
}

@Serializable
class LunarChapterListResponse(
    val data: List<LunarChapterDto> = emptyList(),
)

@Serializable
class LunarChapterDto(
    val chapter: String,
    @SerialName("chapter_number") val chapterNumber: Float,
    @SerialName("chapter_subnumber") val chapterSubnumber: Float? = null,
    @SerialName("chapter_title") val chapterTitle: String? = null,
    val language: String,
    @SerialName("uploaded_at") val uploadedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String, isLocked: Boolean): SChapter = SChapter.create().apply {
        url = "/manga/$mangaSlug/$chapter?lang=$language"
        val prefix = if (isLocked) "🔒 " else ""
        val chapterName = chapter.removeSuffix(".00").removeSuffix(".0")
        val chapterNum = "Chapter $chapterName"
        name = prefix + if (chapterTitle.isNullOrBlank()) {
            chapterNum
        } else if (chapterTitle.contains(chapterNum, ignoreCase = true) ||
            chapterTitle.contains("Ch.$chapterName", ignoreCase = true) ||
            chapterTitle.contains("Volume", ignoreCase = true) ||
            chapterTitle.contains("Vol.", ignoreCase = true)
        ) {
            chapterTitle
        } else {
            "$chapterNum: $chapterTitle"
        }
        chapter_number = chapter.toFloatOrNull() ?: this@LunarChapterDto.chapterNumber
        date_upload = uploadedAt?.let { parseChapterDate(it) } ?: 0L
        scanlator = language.uppercase(Locale.ROOT)
    }

    private fun parseChapterDate(date: String): Long = DATE_FORMAT.tryParseDateTime(date)
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    .withZone(ZoneOffset.UTC)

/**
 * The two seed strings every chapter token and session key is derived from.
 */
class LunarSeeds(val axis: String, val pitch: String)

/**
 * The seeds are not a field of their own: they arrive as the props of a reader component,
 * under names randomised per response. One prop holds a masked, reversed-base64 header that
 * names the props carrying the payload; the payload bytes are then chained through a small
 * byte program described by that same header.
 *
 * Several decoy prop sets are rendered alongside the real one, so [toSeeds] is also what
 * identifies it - a decoy either fails to produce a header or misses the magic prefix.
 */
@Serializable(with = LunarSeedPropsDto.Serializer::class)
class LunarSeedPropsDto(private val props: Map<String, String>) {

    fun toSeeds(): LunarSeeds? {
        for ((name, value) in props) {
            val header = parseHeader(name, value) ?: continue
            val bytes = header.decode(props) ?: continue

            if (bytes.size < HEADER_SIZE) continue
            if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1 || bytes[2] != MAGIC_2) return null

            val axisLength = bytes[3] shl 8 or bytes[4]
            val pitchLength = bytes[5] shl 8 or bytes[6]
            if (axisLength <= 0 || pitchLength <= 0 || HEADER_SIZE + axisLength + pitchLength > bytes.size) return null

            return LunarSeeds(
                axis = bytes.toLatin1(HEADER_SIZE, axisLength),
                pitch = bytes.toLatin1(HEADER_SIZE + axisLength, pitchLength),
            )
        }
        return null
    }

    /** The header prop is masked with a rolling key derived from its own name. */
    private fun parseHeader(name: String, value: String): SeedHeader? {
        val decoded = try {
            Base64.decode(value.reversed(), Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }

        var nameHash = 0
        for (char in name) nameHash = 31 * nameHash + char.code and 0xFF

        val unmasked = String(
            CharArray(decoded.size) { i ->
                ((decoded[i].toInt() and 0xFF) xor (nameHash + 37 * i and 0xFF)).toChar()
            },
        )

        val parts = unmasked.split("|")
        if (parts.size != HEADER_FIELDS || parts[0] != HEADER_VERSION) return null

        val seed = parts[1].toIntOrNull(16) ?: return null
        val multiplier = parts[2].toIntOrNull(16) ?: return null
        val increment = parts[3].toIntOrNull(16) ?: return null

        val programText = parts[4]
        if (programText.isEmpty() || programText.length % 3 != 0) return null

        val program = buildList {
            for (i in programText.indices step 3) {
                val op = programText.substring(i, i + 1).toIntOrNull(16) ?: return null
                val arg = programText.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                if (op > MAX_OPCODE) return null
                add(op to arg)
            }
        }

        val names = parts[5].split(".").filter { it.isNotEmpty() }
        if (names.isEmpty()) return null

        return SeedHeader(seed, multiplier, increment, program, names)
    }

    private class SeedHeader(
        private val seed: Int,
        private val multiplier: Int,
        private val increment: Int,
        private val program: List<Pair<Int, Int>>,
        private val names: List<String>,
    ) {
        /**
         * The named props concatenate into one hex string, each byte chained to the previous
         * one and to an LCG keyed by the header, then run backwards through the program.
         */
        fun decode(props: Map<String, String>): List<Int>? {
            val hex = names.joinToString("") { props[it].orEmpty() }
            if (hex.length < 2 || hex.length % 2 != 0) return null

            val out = ArrayList<Int>(hex.length / 2)
            var state = seed and 0xFF
            var previous = 0

            for (i in hex.indices step 2) {
                val current = hex.substring(i, i + 2).toIntOrNull(16) ?: return null
                state = state * multiplier + increment and 0xFF
                out.add(unprogram(current xor previous, i / 2, state))
                previous = current
            }
            return out
        }

        private fun unprogram(value: Int, index: Int, state: Int): Int {
            var n = value and 0xFF
            for ((op, arg) in program.asReversed()) {
                n = when (op) {
                    0 -> n xor arg
                    1 -> n - arg
                    2 -> {
                        val shift = (arg and 7).takeIf { it != 0 } ?: 1
                        (n and 0xFF) ushr shift or (n shl 8 - shift)
                    }
                    3 -> (n and 15) shl 4 or ((n and 0xFF) ushr 4)
                    4 -> n xor state
                    5 -> n xor (index * (1 or arg) + arg and 0xFF)
                    6 -> n.inv()
                    else -> arg - n
                } and 0xFF
            }
            return n
        }
    }

    private fun List<Int>.toLatin1(offset: Int, length: Int) = String(CharArray(length) { this[offset + it].toChar() })

    /** The prop names change per response, so the object can only be modelled as a map. */
    object Serializer : KSerializer<LunarSeedPropsDto> {
        private val delegate = MapSerializer(String.serializer(), String.serializer())

        override val descriptor: SerialDescriptor get() = delegate.descriptor

        override fun deserialize(decoder: Decoder) = LunarSeedPropsDto(delegate.deserialize(decoder))

        override fun serialize(encoder: Encoder, value: LunarSeedPropsDto) = delegate.serialize(encoder, value.props)
    }

    companion object {
        private const val HEADER_SIZE = 7
        private const val HEADER_FIELDS = 6
        private const val HEADER_VERSION = "3"
        private const val MAX_OPCODE = 7
        private const val MAGIC_0 = 167
        private const val MAGIC_1 = 62
        private const val MAGIC_2 = 145

        /**
         * Matches the flight element holding the seeds. Decoys share the shape, so the payload
         * has to be decoded to tell them apart.
         */
        fun matches(element: JsonElement): Boolean = element is JsonObject &&
            element.isNotEmpty() &&
            element.values.all { it is JsonPrimitive && it.isString } &&
            LunarSeedPropsDto(element.mapValues { (_, value) -> value.jsonPrimitive.content }).toSeeds() != null
    }
}

@Serializable
class LunarPageListResponse(
    val data: LunarPageListData? = null,
)

@Serializable
class LunarPageListData(
    val images: List<String> = emptyList(),
    @SerialName("session_data") val sessionData: String? = null,
)

@Serializable
class LunarPageListDecrypted(
    val data: LunarPageListData,
)

@Serializable
class SecretKeyDto(
    val secretKey: String,
)

@Serializable
class LunarRecentResponse(
    @SerialName("our_mangas") val mangas: List<LunarMangaDto> = emptyList(),
    val page: Int = 0,
    val limit: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0,
)

@Serializable
class LunarPasswordInfoResponse(
    @SerialName("chapter_passwords") val chapterPasswords: List<LunarChapterPasswordDto> = emptyList(),
    @SerialName("has_series_password") val hasSeriesPassword: Boolean = false,
)

@Serializable
class LunarChapterPasswordDto(
    @SerialName("chapter_number") val chapterNumber: String? = null,
    val language: String? = null,
)

@Serializable
class ViewRequestBody(
    val slug: String,
    val chapter: String,
    val language: String,
)
