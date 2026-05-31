package eu.kanade.tachiyomi.extension.en.jnovel

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class SeriesResponse(
    val seriesList: SeriesList,
)

@Serializable
class SeriesList(
    val series: List<SeriesItem>,
    private val nextPageToken: String,
) {
    fun hasNextPage() = nextPageToken.isNotEmpty()
}

@Serializable
class SeriesItem(
    private val slug: String,
    private val title: String,
    private val cover: Cover?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = slug
        title = this@SeriesItem.title
        thumbnail_url = cover?.coverUrl?.toHttpUrl()?.newBuilder()?.setPathSegment(2, "1200")?.build()?.toString()
    }
}

@Serializable
class Cover(
    val coverUrl: String?,
)

@Serializable
class SeriesDetailsResponse(
    val series: SeriesDetails,
    val volumes: List<Volume>,
)

@Serializable
class SeriesDetails(
    val title: String,
    private val description: String?,
    private val tags: List<String>?,
    private val status: Int?,
    private val banner: Banner?,
) {
    fun toSManga(creators: List<Creator>?): SManga = SManga.create().apply {
        title = this@SeriesDetails.title
        description = this@SeriesDetails.description
        genre = tags?.joinToString()
        status = when (this@SeriesDetails.status) {
            0 -> SManga.ONGOING
            1 -> SManga.COMPLETED
            2 -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        author = creators?.filter { it.role == 1 }?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.joinToString()
        artist = creators?.filter { it.role == 4 }?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.joinToString()
        thumbnail_url = banner?.originalUrl
    }
}

@Serializable
class Volume(
    val parts: List<Part> = emptyList(),
    val volume: VolumeInfo?,
)

@Serializable
class VolumeInfo(
    val creators: List<Creator>?,
    val owned: Boolean?,
)

@Serializable
class Creator(
    val name: String?,
    val role: Int?,
)

@Serializable
class Banner(
    val originalUrl: String?,
)

@Serializable
class Part(
    private val slug: String,
    private val title: String,
    private val launch: Time?,
    private val number: Int?,
    private val preview: Boolean?,
    private val rental: Rental?,
) {
    fun isLocked(owned: Boolean): Boolean = !owned && preview == false && rental == null

    fun toSChapter(mangaTitle: String, owned: Boolean): SChapter = SChapter.create().apply {
        val lock = if (isLocked(owned)) "🔒 " else ""
        val chapterName = title.removePrefix(mangaTitle).trim().ifEmpty { title }
        url = slug
        name = lock + chapterName
        date_upload = launch?.seconds?.toLong()?.times(1000L) ?: 0L
        chapter_number = number?.toFloat() ?: -1f
    }
}

@Serializable
class Time(
    val seconds: String?,
)

@Serializable
class Rental(
    val expiresAt: Time?,
)
