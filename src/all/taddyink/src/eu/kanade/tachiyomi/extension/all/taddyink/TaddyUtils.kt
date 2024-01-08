package eu.kanade.tachiyomi.extension.all.taddyink

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

object TaddyUtils {
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    fun getManga(comicObj: Comic): SManga {
        val name = comicObj.name
        val sssUrl = comicObj.url
        val sssDescription = comicObj.description
        val genres = comicObj.genres.orEmpty()
            .mapNotNull { genreMap[it] }
            .joinToString()

        val creators = comicObj.creators
            ?.mapNotNull { it.name }
            ?.joinToString()

        val thumbnailBaseUrl = comicObj.coverImage?.base_url ?: ""
        val thumbnail = comicObj.coverImage?.cover_sm ?: ""
        val thumbnailUrl = if (thumbnailBaseUrl.isNotEmpty() && thumbnail.isNotEmpty()) "$thumbnailBaseUrl$thumbnail" else ""

        return SManga.create().apply {
            url = sssUrl
            title = name
            creators?.takeIf { it.isNotBlank() }?.let { author = it }
            description = sssDescription
            thumbnail_url = thumbnailUrl
            status = SManga.ONGOING
            genre = genres
            initialized = true
        }
    }

    fun getTime(timeString: String): Long {
        return runCatching { formatter.parse(timeString)?.time }
            .getOrNull() ?: 0L
    }

    val genrePairs: List<Pair<String, String>> = listOf(
        Pair("", ""),
        Pair("Action", "COMICSERIES_ACTION"),
        Pair("Comedy", "COMICSERIES_COMEDY"),
        Pair("Drama", "COMICSERIES_DRAMA"),
        Pair("Educational", "COMICSERIES_EDUCATIONAL"),
        Pair("Fantasy", "COMICSERIES_FANTASY"),
        Pair("Historical", "COMICSERIES_HISTORICAL"),
        Pair("Horror", "COMICSERIES_HORROR"),
        Pair("Inspirational", "COMICSERIES_INSPIRATIONAL"),
        Pair("Mystery", "COMICSERIES_MYSTERY"),
        Pair("Romance", "COMICSERIES_ROMANCE"),
        Pair("Sci-Fi", "COMICSERIES_SCI_FI"),
        Pair("Slice Of Life", "COMICSERIES_SLICE_OF_LIFE"),
        Pair("Superhero", "COMICSERIES_SUPERHERO"),
        Pair("Supernatural", "COMICSERIES_SUPERNATURAL"),
        Pair("Wholesome", "COMICSERIES_WHOLESOME"),
        Pair("BL (Boy Love)", "COMICSERIES_BL"),
        Pair("GL (Girl Love)", "COMICSERIES_GL"),
        Pair("LGBTQ+", "COMICSERIES_LGBTQ"),
        Pair("Thriller", "COMICSERIES_THRILLER"),
        Pair("Zombies", "COMICSERIES_ZOMBIES"),
        Pair("Post Apocalyptic", "COMICSERIES_POST_APOCALYPTIC"),
        Pair("School", "COMICSERIES_SCHOOL"),
        Pair("Sports", "COMICSERIES_SPORTS"),
        Pair("Animals", "COMICSERIES_ANIMALS"),
        Pair("Gaming", "COMICSERIES_GAMING"),
    )

    val genreMap: Map<String, String> = genrePairs.associateBy({ it.second }, { it.first })
}

@Serializable
data class ComicResults(
    val status: String,
    val comicseries: List<Comic> = emptyList(),
)

@Serializable
data class Comic(
    val identifier: String? = null,
    val name: String = "Unknown",
    val url: String,
    val description: String? = null,
    val genres: List<String>? = emptyList(),
    val creators: List<Creator>? = emptyList(),
    val coverImage: CoverImage? = null,
    val bannerImage: BannerImage? = null,
    val thumbnailImage: ThumbnailImage? = null,
    val contentRating: String? = null,
    val inLanguage: String? = null,
    val seriesType: String? = null,
    val issues: List<Chapter>? = emptyList(),
)

@Serializable
data class CoverImage(
    val base_url: String?,
    val cover_sm: String?,
    val cover_md: String?,
    val cover_lg: String?,
)

@Serializable
data class BannerImage(
    val base_url: String?,
    val banner_sm: String?,
    val banner_md: String?,
    val banner_lg: String?,
)

@Serializable
data class ThumbnailImage(
    val base_url: String?,
    val thumbnail: String?,
)

@Serializable
data class Creator(
    val identifier: String? = null,
    val name: String? = null,
)

@Serializable
data class Chapter(
    val identifier: String,
    val name: String,
    val datePublished: String,
    val stories: List<Story>? = emptyList(),
)

@Serializable
data class Story(
    val storyImage: StoryImage?,
)

@Serializable
data class StoryImage(
    val base_url: String?,
    val story: String?,
)
