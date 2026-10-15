package eu.kanade.tachiyomi.extension.ja.nicomanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Payload(
    val manga: NManga? = null,
    val images: List<String>? = null,
    @SerialName("chapters_list") val chaptersList: List<NChapter>? = null,
)

@Serializable
class NManga(
    val n: String,
    val c: String? = null,
    val artists: String? = null,
    @SerialName("other_name") val otherName: String? = null,
    val description: String? = null,
    @SerialName("status_text") val statusText: String? = null,
    @SerialName("authors_list") val authorsList: List<NLink> = emptyList(),
    @SerialName("genres_list") val genresList: List<NLink> = emptyList(),
)

@Serializable
class NChapter(
    val chapter: String? = null,
    val n: String? = null,
    val t: String? = null,
    val ur: String? = null,
)

@Serializable
class NLink(
    val n: String = "",
)

@Serializable
class GenresPayload(
    val genres: List<NGenre> = emptyList(),
)

@Serializable
class NGenre(
    val id: String,
    val name: String,
)
