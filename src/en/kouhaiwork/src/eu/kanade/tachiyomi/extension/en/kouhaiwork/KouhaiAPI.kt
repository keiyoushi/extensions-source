package eu.kanade.tachiyomi.extension.en.kouhaiwork

import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

const val ID_QUERY = "id:"

const val API_URL = "https://api.kouhai.work/v3"

const val STORAGE_URL = "https://api.kouhai.work/storage/"

private const val ISO_DATE = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"

private val dateFormat = SimpleDateFormat(ISO_DATE, Locale.ROOT)

private val decimalFormat = DecimalFormat("#.##")

@Serializable
data class KouhaiSeries(
    val id: Int,
    val title: String,
    val cover: String,
) {
    inline val url get() = id.toString()

    inline val thumbnail get() = STORAGE_URL + cover

    override fun toString() = title.trim()
}

@Serializable
data class KouhaiSeriesDetails(
    val synopsis: String,
    val status: String,
    val alternative_titles: List<String>? = null,
    val artists: List<KouhaiTag>? = null,
    val authors: List<KouhaiTag>? = null,
    val genres: List<KouhaiTag>? = null,
    val themes: List<KouhaiTag>? = null,
    val demographics: List<KouhaiTag>? = null,
    val chapters: List<KouhaiChapter>,
) {
    val tags by lazy {
        genres.orEmpty() + themes.orEmpty() + demographics.orEmpty()
    }

    override fun toString() = buildString {
        append(synopsis)
        alternative_titles?.joinTo(
            this,
            "\n",
            "\n\nAlternative Names:\n",
        )
    }
}

@Serializable
data class KouhaiChapter(
    val id: Int,
    val volume: Int? = null,
    val number: Float,
    val name: String? = null,
    val groups: List<KouhaiTag>,
    val updated_at: String,
) {
    inline val url get() = id.toString()

    val timestamp by lazy {
        dateFormat.parse(updated_at)?.time ?: 0L
    }

    override fun toString() = buildString {
        volume?.let {
            append("[Vol. ").append(it).append("] ")
        }
        append("Chapter ")
        append(decimalFormat.format(number))
        name?.let { append(" - ").append(it) }
    }
}

@Serializable
data class KouhaiTag(
    private val id: Int,
    private val name: String,
) {
    override fun toString() = name
}

@Serializable
data class KouhaiTagList(
    val genres: List<KouhaiTag>,
    val themes: List<KouhaiTag>,
    val demographics: List<KouhaiTag>,
    val status: KouhaiTag?,
)

@Serializable
data class KouhaiPages(
    private val pages: List<KouhaiMedia>,
) : Iterable<KouhaiMedia> by pages

@Serializable
data class KouhaiMedia(private val media: String) {
    override fun toString() = STORAGE_URL + media
}

typealias KouhaiSearch = List<String>

inline val KouhaiSearch.url get() = this[0]

inline val KouhaiSearch.title get() = this[1].trim()

inline val KouhaiSearch.thumbnail get() = STORAGE_URL + last()
