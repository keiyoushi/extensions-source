package eu.kanade.tachiyomi.extension.en.manta

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
data class MantaResponse<T>(
    val data: T,
    val status: Status? = null,
)

@Serializable
data class Series<T : Any>(
    val data: T,
    val id: Int,
    val image: Cover,
    val episodes: List<Episode>? = null,
) {
    override fun toString() = data.toString()
}

@Serializable
data class Title(private val title: Name) {
    override fun toString() = title.toString()
}

@Serializable
data class Details(
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

    override fun toString() = description.toString()
}

@Serializable
data class Episode(
    val id: Int,
    val ord: Int,
    val data: Data?,
    val lockData: LockData,
    private val createdAt: String,
    val cutImages: List<Image>? = null,
) {
    val timestamp: Long
        get() = createdAt.timestamp

    override fun toString() = buildString {
        append(data?.title ?: "Episode $ord")
        if (lockData.isLocked) append(" 🔒")
    }
}

@Serializable
data class Data(val title: String? = null)

@Serializable
data class LockData(private val state: Int) {
    // TODO: check for more unlocked states
    val isLocked: Boolean
        get() = state !in arrayOf(110, 130)
}

@Serializable
data class Creator(
    private val name: String,
    val role: String,
) {
    override fun toString() = name
}

@Serializable
data class Description(
    private val long: String,
    private val short: String? = null,
) {
    override fun toString() = listOfNotNull(short, long).joinToString("\n\n")
}

@Serializable
@Suppress("PrivatePropertyName")
data class Cover(
    private val `1280x1840_480`: Image? = null,
    private val `1280x1840_720`: Image? = null,
    private val `1440x3072`: Image? = null,
    private val `1440x1440_480`: Image? = null,
) {
    override fun toString() = (`1280x1840_480` ?: `1280x1840_720` ?: `1440x3072` ?: `1440x1440_480`)?.toString().orEmpty()
}

@Serializable
data class Image(private val downloadUrl: String) {
    override fun toString() = downloadUrl
}

@Serializable
data class Tag(private val name: Name) {
    override fun toString() = name.toString()
}

@Serializable
data class Name(private val en: String) {
    override fun toString() = en
}

@Serializable
data class Status(
    private val description: String,
    private val message: String,
) {
    override fun toString() = "$description: $message"
}
