package eu.kanade.tachiyomi.extension.all.taddyink

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object TaddyUtils {
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

        val thumbnailBaseUrl = comicObj.coverImage?.baseUrl ?: ""
        val thumbnail = comicObj.coverImage?.coverSm ?: ""
        val thumbnailUrl = if (thumbnailBaseUrl.isNotEmpty() && thumbnail.isNotEmpty()) "$thumbnailBaseUrl$thumbnail" else ""

        return SManga.create().apply {
            url = sssUrl
            title = name
            creators?.takeIf { it.isNotBlank() }?.let { author = it }
            description = sssDescription
            thumbnail_url = thumbnailUrl
            status = SManga.ONGOING
            genre = genres
        }
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
class ComicResults(
    val comicseries: List<Comic> = emptyList(),
)

@Serializable
class Comic(
    val name: String = "Unknown",
    val url: String,
    val description: String? = null,
    val genres: List<String>? = emptyList(),
    val creators: List<Creator>? = emptyList(),
    val coverImage: CoverImage? = null,
    val issues: List<Chapter>? = emptyList(),
)

@Serializable
class CoverImage(
    @SerialName("base_url") val baseUrl: String?,
    @SerialName("cover_sm") val coverSm: String?,
)

@Serializable
class Creator(
    val name: String? = null,
)

@Serializable
class Chapter(
    val identifier: String,
    val name: String,
    val datePublished: String,
    val stories: List<Story>? = emptyList(),
)

@Serializable
class Story(
    val storyImage: StoryImage?,
)

@Serializable
class StoryImage(
    @SerialName("base_url") val baseUrl: String?,
    val story: String?,
)
