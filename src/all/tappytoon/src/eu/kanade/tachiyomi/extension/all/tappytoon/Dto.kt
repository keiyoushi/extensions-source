package eu.kanade.tachiyomi.extension.all.tappytoon

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

interface Accessible {
    val isAccessible: Boolean
}

inline val <A : Accessible> List<A>.accessible: List<A>
    get() = filter { it.isAccessible }

@Serializable
class Comic(
    private val id: Int,
    private val title: String,
    private val slug: String,
    private val longDescription: String,
    private val posterThumbnailUrl: String,
    private val isHiatus: Boolean,
    override val isAccessible: Boolean,
    private val isCompleted: Boolean,
    private val isSuperHighQualitySupported: Boolean,
    val ageRating: AgeRating,
    private val genres: List<Name>,
    private val authors: List<Name>,
) : Accessible {
    fun toSManga(matureLoginMessage: String) = SManga.create().apply {
        url = "$slug|$id"
        title = this@Comic.title
        description = MarkdownDescription.Builder()
            .description(matureLoginMessage.takeUnless { ageRating.canUserRead })
            .description(longDescription)
            .build()
        thumbnail_url = posterThumbnailUrl
        author = authors.joinToString().ifEmpty { null }
        artist = author
        genre = buildList {
            genres.mapTo(this, Name::toString)
            add("Rating: $ageRating")
            if (isSuperHighQualitySupported) add("HD")
        }.joinToString()
        status = when {
            isCompleted -> SManga.COMPLETED
            !isHiatus -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class AgeRating(
    private val name: String,
    val canUserRead: Boolean,
) {
    override fun toString() = name
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
        if (subtitle.isNotBlank()) {
            append(" - ")
            append(subtitle.trim())
        }
    }

    val isLocked: Boolean
        get() = !isFree && !isUserUnlocked && !isUserRented
}

@Serializable
class Media(private val media: List<Image>) : List<Image> by media

@Serializable
class Image(private val path: String) {
    override fun toString() = path
}

@Serializable
class ErrorResponse(val message: String)

@Serializable
class BookState(
    val axios: JsonElement,
    val entities: Entities,
)

@Serializable
class Entities(val comics: Map<String, ComicSlug> = emptyMap())

@Serializable
class ComicSlug(
    private val id: Int,
    val slug: String,
) {
    override fun toString() = "$slug|$id"
}

@Serializable
class AxiosHeaders(
    @SerialName("Authorization") val authorization: String,
    @SerialName("X-Device-Uuid") val deviceUuid: String,
)
