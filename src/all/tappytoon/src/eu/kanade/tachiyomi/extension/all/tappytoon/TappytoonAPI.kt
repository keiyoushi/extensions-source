package eu.kanade.tachiyomi.extension.all.tappytoon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface Accessible {
    val isAccessible: Boolean
}

inline val <A : Accessible> List<A>.accessible: List<A>
    get() = filter { it.isAccessible }

@Serializable
class Comic(
    private val id: Int,
    val title: String,
    private val slug: String,
    val longDescription: String,
    val posterThumbnailUrl: String,
    val isHiatus: Boolean,
    override val isAccessible: Boolean,
    val isCompleted: Boolean,
    val ageRating: Name,
    val genres: List<Name>,
    val authors: List<Name>,
) : Accessible {
    override fun toString() = "$slug|$id"
}

@Serializable
class Name(private val name: String) {
    override fun toString() = name
}

@Serializable
class Chapter(
    val id: Int,
    val order: Float,
    private val title: String,
    private val subtitle: String,
    override val isAccessible: Boolean,
    private val isFree: Boolean,
    private val isUserUnlocked: Boolean,
    private val isUserRented: Boolean,
    val willAccessibleAt: String,
) : Accessible {
    override fun toString() = buildString {
        append(title)
        if (subtitle.isNotEmpty()) {
            append(" - ")
            append(subtitle)
        }
        if (!isFree && !(isUserUnlocked || isUserRented)) {
            append(" \uD83D\uDD12")
        }
    }
}

@Serializable
class Media(private val media: List<URL>) : List<URL> by media

@Serializable
class URL(private val path: String) {
    override fun toString() = path
}

@Serializable
class ErrorResponse(val message: String)

@Serializable
class NextData(val props: NextProps)

@Serializable
class NextProps(val initialState: InitialState)

@Serializable
class InitialState(val axios: Axios)

@Serializable
class Axios(val headers: AxiosHeaders)

@Serializable
class AxiosHeaders(
    @SerialName("Authorization") val authorization: String,
    @SerialName("X-Device-Uuid") val deviceUuid: String,
)
