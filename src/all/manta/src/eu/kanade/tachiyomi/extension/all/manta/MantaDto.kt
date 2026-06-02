package eu.kanade.tachiyomi.extension.all.manta

import keiyoushi.utils.tryParse
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
    fun asString(lang: String) = when (data) {
        is Title -> data.asString(lang)
        is Details -> data.asString(lang)
        else -> data.toString()
    }

    override fun toString() = data.toString()
}

@Serializable
class Title(private val title: Name) {
    fun asString(lang: String) = title.asString(lang)
    override fun toString() = title.toString()
}

@Serializable
class Details(
    val tags: List<Tag>,
    val isCompleted: Boolean? = null,
    private val description: Description,
    private val creators: List<Creator>,
) {
    val artists by lazy {
        creators.filter { it.role == "Illustration" }
    }

    val authors by lazy {
        creators.filter { it.role != "Illustration" }.ifEmpty { creators }
    }

    fun asString(lang: String) = description.asString(lang)

    override fun toString() = description.toString()
}

@Serializable
class Episode(
    val id: Int,
    val ord: Int,
    val data: EpisodeData?,
    val lockData: LockData,
    private val createdAt: String,
    val cutImages: List<Image>? = null,
) {
    val timestamp: Long
        get() = createdAt.timestamp

    fun asString(lang: String) = buildString {
        val episodeTitle = data?.title
        if (episodeTitle != null) {
            append(episodeTitle)
        } else {
            append(if (lang == "es") "Episodio" else "Episode")
            append(" $ord")
        }
        if (lockData.isLocked) append(" 🔒")
    }

    override fun toString() = asString("en")
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
    private val long: String,
    private val short: String? = null,
) {
    fun asString(lang: String) = listOfNotNull(short, long).joinToString("\n\n")

    override fun toString() = asString("en")
}

@Serializable
class Cover(
    private val `1280x1840_480`: Image? = null,
    private val `1280x1840_720`: Image? = null,
    private val `1440x3072`: Image? = null,
    private val `1440x1440_480`: Image? = null,
) {
    override fun toString() = (`1280x1840_480` ?: `1280x1840_720` ?: `1440x3072` ?: `1440x1440_480`)?.toString().orEmpty()
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
    private val en: String? = null,
    private val es: String? = null,
) {
    fun asString(lang: String) = if (lang == "es") es ?: en.orEmpty() else en ?: es.orEmpty()
    override fun toString() = en ?: es ?: ""
}

@Serializable
class Status(
    private val description: String,
    private val message: String,
) {
    override fun toString() = "$description: $message"
}
