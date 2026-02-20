package eu.kanade.tachiyomi.extension.all.yskcomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup
import java.util.Locale

typealias PopularDto = ResponseDto<List<MangaPopularRaw>>
typealias LatestDto = ResponseDto<ResponseInnerDto<MangaLatestRaw>>
typealias SearchDto = ResponseDto<List<MangaSearchRaw>>
typealias DetailsDto = ResponseDto<DetailsRaw>
typealias ChapterDto = ResponseDto<ResponseInnerDto<ChapterRaw>>
typealias PageDto = ResponseDto<List<String>>

@Serializable
class ResponseDto<T>(val data: T)

@Serializable
class ResponseInnerDto<T>(
    @SerialName("data_messages") val dataMessages: List<T>,
    val meta: Metadata,
)

@Serializable
class Metadata(
    @SerialName("link_next") val linkNext: String?,
)

@Serializable
class NamedEntity(
    val name: String,
)

private fun getRatingString(rate: String, rateCount: Int): String {
    val ratingValue = rate.toDoubleOrNull() ?: 0.0
    val ratingStar = when {
        ratingValue >= 4.75 -> "★★★★★"
        ratingValue >= 4.25 -> "★★★★✬"
        ratingValue >= 3.75 -> "★★★★☆"
        ratingValue >= 3.25 -> "★★★✬☆"
        ratingValue >= 2.75 -> "★★★☆☆"
        ratingValue >= 2.25 -> "★★✬☆☆"
        ratingValue >= 1.75 -> "★★☆☆☆"
        ratingValue >= 1.25 -> "★✬☆☆☆"
        ratingValue >= 0.75 -> "★☆☆☆☆"
        ratingValue >= 0.25 -> "✬☆☆☆☆"
        else -> "☆☆☆☆☆"
    }
    return if (ratingValue > 0.0) {
        buildString {
            append("$ratingStar $rate")
            if (rateCount > 0) {
                append(" ($rateCount)")
            }
        }
    } else {
        ""
    }
}

// ---

@Serializable
class MangaPopularRaw(
    val image: String,
    @SerialName("full_name") val fullName: String,
    val slug: String,
    val rate: String,
    val writer: NamedEntity,
    val publisher: NamedEntity,
    val genres: List<NamedEntity>,
    @JsonNames("descrition") val description: String,
) {
    fun toSManga(lang: String) = SManga.create().apply {
        url = "/$lang/comic/$slug"
        title = fullName
        author = writer.name
        description = buildString {
            val ratingStr = getRatingString(rate, 0)
            if (ratingStr.isNotEmpty()) {
                append(ratingStr, "\n")
            }
            append("Publisher: ", publisher.name, "\n")

            append("\n")

            val plainText = Jsoup
                .parseBodyFragment(this@MangaPopularRaw.description)
                .wholeText()
                .trim()
            append(plainText)
        }
        genre = genres.joinToString { it.name }
        status = SManga.UNKNOWN
        thumbnail_url = image
    }
}

@Serializable
class MangaLatestRaw(
    val image: String,
    @SerialName("full_name") val fullName: String,
    val slug: String,
    val rate: String,
    @SerialName("rate_count") val rateCount: Int,
    val writer: String,
    val genres: List<NamedEntity>,
) {
    fun toSManga(lang: String) = SManga.create().apply {
        url = "/$lang/comic/$slug"
        title = fullName
        author = writer
        description = buildString {
            val ratingStr = getRatingString(rate, rateCount)
            if (ratingStr.isNotEmpty()) {
                append(ratingStr, "\n")
            }
            trimEnd()
        }
        genre = genres.joinToString { it.name }
        status = SManga.UNKNOWN
        thumbnail_url = image
    }
}

@Serializable
class MangaSearchRaw(
    @SerialName("full_name") val fullName: String,
    val slug: String,
    val image: String,
) {
    fun toSManga(lang: String) = SManga.create().apply {
        url = "/$lang/comic/$slug"
        title = fullName
        status = SManga.UNKNOWN
        thumbnail_url = image
    }
}

// ---

@Serializable
class DetailsRaw(
    @SerialName("full_name") val fullName: String,
    val slug: String,
    val image: String,
    val rate: String,
    @SerialName("rate_count") val rateCount: Int,
    @SerialName("language_code") val languageCode: String,
    val writer: NamedEntity,
    val publisher: NamedEntity,
    val genres: List<NamedEntity>,
    val artists: List<NamedEntity>,
    val status: String,
    val description: String,
    @SerialName("published_at") val publishedAt: String,
) {
    fun toSManga(lang: String): SManga = SManga.create().apply {
        url = "/$lang/comic/$slug"
        title = fullName
        artist = artists.joinToString { it.name }
        author = writer.name
        description = buildString {
            if (lang != languageCode) {
                // The site redirects to the correct language in this case. Not possible here.
                // The user has to intentionally open an invalid URL to arrive here.
                val language = runCatching {
                    Locale(lang).getDisplayLanguage(Locale.ENGLISH)
                }.getOrDefault(lang)

                append("**Sorry, this content is not available in ", language, ".**\n\n")
            }

            val ratingString = getRatingString(rate, rateCount)
            if (ratingString.isNotEmpty()) {
                append(ratingString, "\n")
            }

            append("Publisher: ", publisher.name, "\n")
            append("Published at: ", publishedAt, "\n")

            append("\n")

            val plainText = Jsoup
                .parseBodyFragment(this@DetailsRaw.description)
                .wholeText()
                .trim()
            append(plainText)
        }
        genre = genres.joinToString { it.name }
        status = when (this@DetailsRaw.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = image
    }
}

// ---

@Serializable
class ChapterRaw(
    val slug: String,
    val rank: String,
) {
    fun toSChapter(lang: String) = SChapter.create().apply {
        url = "/$lang/chapter/$slug"
        name = "#$rank"
    }
}
