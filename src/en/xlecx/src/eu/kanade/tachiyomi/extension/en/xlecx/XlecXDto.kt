package eu.kanade.tachiyomi.extension.en.xlecx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JsonLdDto(
    @SerialName("@graph")
    val graph: List<BookDto>,
)

@Serializable
class BookDto(
    val datePublished: String,
    val dateModified: String? = null,
    val image: List<String>,
)
