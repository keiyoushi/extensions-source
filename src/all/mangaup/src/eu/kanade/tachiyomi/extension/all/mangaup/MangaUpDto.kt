package eu.kanade.tachiyomi.extension.all.mangaup

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class MangaUpSearch(
    val titles: List<MangaUpTitle> = emptyList(),
)

@Serializable
data class MangaUpViewer(
    val pages: List<MangaUpPage> = emptyList(),
)

@Serializable
data class MangaUpTitle(
    @SerialName("titleId") val id: Int? = null,
    @SerialName("titleName") val name: String,
    val authorName: String? = null,
    val description: String? = null,
    val copyright: String? = null,
    val thumbnailUrl: String? = null,
    val mainThumbnailUrl: String? = null,
    val bookmarkCount: Int? = null,
    val genres: List<MangaUpGenre> = emptyList(),
    val chapters: List<MangaUpChapter> = emptyList(),
) {

    private val fullDescription: String
        get() = buildString {
            description?.let { append(it) }
            copyright?.let { append("\n\n" + it.replace("(C)", "© ")) }
        }

    private val isFinished: Boolean
        get() = chapters.any { it.mainName.contains("final chapter", ignoreCase = true) }

    val readableChapters: List<MangaUpChapter>
        get() = chapters.filter(MangaUpChapter::isReadable).reversed()

    fun toSManga(): SManga = SManga.create().apply {
        title = name
        author = authorName
        description = fullDescription.trim()
        genre = genres.joinToString { it.name }
        status = if (isFinished) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = mainThumbnailUrl ?: thumbnailUrl
        url = "/manga/$id"
    }
}

@Serializable
data class MangaUpGenre(
    val id: Int,
    val name: String,
)

@Serializable
data class MangaUpChapter(
    val id: Int,
    val mainName: String,
    val subName: String? = null,
    val price: Int? = null,
    val published: Int,
    val badge: MangaUpBadge = MangaUpBadge.FREE,
    val available: Boolean = false,
) {

    val isReadable: Boolean
        get() = badge == MangaUpBadge.FREE && available

    fun toSChapter(titleId: Int): SChapter = SChapter.create().apply {
        name = mainName.replace(WRONG_SPACING_REGEX, "-$1") +
            if (!subName.isNullOrEmpty()) ": $subName" else ""
        date_upload = (published * 1000L).takeIf { it <= Date().time } ?: 0L
        url = "/manga/$titleId/$id"
    }

    companion object {
        private val WRONG_SPACING_REGEX = "\\s+-(\\d+)$".toRegex()
    }
}

enum class MangaUpBadge {
    FREE,
    ADVANCE,
    UPDATE,
}

@Serializable
data class MangaUpPage(
    val imageUrl: String,
)
