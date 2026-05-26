package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

private val isoDate by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
}

private inline val String?.timestamp: Long
    get() = isoDate.tryParse(this?.substringBefore('.'))

@Serializable
class MantaResponse<T>(
    val data: T,
    val status: Status? = null,
)

@Serializable
class Series<T : Any>(
    val data: T,
    val id: Int,
    val image: Cover,
    val episodes: List<Episode>? = null,
) {
    fun toSManga(lang: String): SManga {
        val series = this
        return SManga.create().apply {
            val titleObj = when (val d = series.data) {
                is Title -> d.title
                is Details -> d.title
                else -> null
            }
            title = titleObj?.asString(lang) ?: ""
            url = series.id.toString()
            thumbnail_url = series.image.toString()

            if (series.data is Details) {
                val details = series.data
                description = details.description.toString()
                genre = details.tags.joinToString { it.asString(lang) }
                artist = details.creators.filter { it.role == "Illustration" }.joinToString()
                author = details.creators.filter { it.role != "Illustration" }.ifEmpty { details.creators }.joinToString()
                status = when (details.isCompleted) {
                    true -> SManga.COMPLETED
                    else -> SManga.ONGOING
                }
                initialized = true
            }
        }
    }
}

@Serializable
class Title(val title: Name)

@Serializable
class Details(
    val title: Name? = null,
    val tags: List<Tag>,
    val isCompleted: Boolean? = null,
    val description: Description,
    val creators: List<Creator>,
)

@Serializable
class Episode(
    val id: Int,
    val ord: Int,
    val data: EpisodeData? = null,
    val lockData: LockData,
    private val createdAt: String,
    val cutImages: List<Image>? = null,
) {
    private val timestamp: Long
        get() = createdAt.timestamp

    private fun asString(lang: String) = buildString {
        val fallback = if (lang == "es") "Episodio" else "Episode"
        append(data?.title ?: "$fallback $ord")
        if (lockData.isLocked) append(" 🔒")
    }

    fun toSChapter(lang: String) = SChapter.create().apply {
        name = asString(lang)
        url = id.toString()
        date_upload = timestamp
        chapter_number = ord.toFloat()
    }
}

@Serializable
class EpisodeData(val title: String? = null)

@Serializable
class LockData(private val state: Int) {
    val isLocked: Boolean
        get() = state !in arrayOf(110, 130)
}

@Serializable
class Creator(
    private val name: String,
    val role: String,
) {
    override fun toString() = name
}

@Serializable
class Description(
    val long: String? = null,
    val short: String? = null,
) {
    override fun toString() = listOfNotNull(short, long).joinToString("\n\n")
}

@Serializable
@Suppress("PrivatePropertyName")
class Cover(
    @SerialName("1280x1840_480") private val size1: Image? = null,
    @SerialName("1280x1840_720") private val size2: Image? = null,
    @SerialName("1440x3072") private val size3: Image? = null,
    @SerialName("1440x1440_480") private val size4: Image? = null,
) {
    override fun toString() = (size1 ?: size2 ?: size3 ?: size4)?.toString().orEmpty()
}

@Serializable
class Image(private val downloadUrl: String) {
    override fun toString() = downloadUrl
}

@Serializable
class Tag(private val name: Name) {
    fun asString(lang: String) = name.asString(lang)

    override fun toString() = name.toString()
}

@Serializable
class Name(
    private val en: String,
    private val es: String? = null,
) {
    fun asString(lang: String) = when (lang) {
        "es" -> es
        else -> en
    } ?: en

    override fun toString() = en
}

@Serializable
class Status(
    private val description: String,
    private val message: String,
) {
    override fun toString() = "$description: $message"
}
