package eu.kanade.tachiyomi.extension.all.tappytoon

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
    val createdAt: String,
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
class URL(private val url: String) {
    override fun toString() = url
}
