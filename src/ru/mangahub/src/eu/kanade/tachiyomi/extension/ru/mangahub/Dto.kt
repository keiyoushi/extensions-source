package eu.kanade.tachiyomi.extension.ru.mangahub

import kotlinx.serialization.Serializable

@Serializable
class SearchData(
    val genres: List<Pair<String, String>>? = emptyList(),
    val tags: List<Pair<String, String>>? = emptyList(),
    val type: List<Pair<String, String>>? = emptyList(),
    val status: List<Pair<String, String>>? = emptyList(),
    val translation: List<Pair<String, String>>? = emptyList(),
    val format: List<Pair<String, String>>? = emptyList(),
    val age: List<Pair<String, String>>? = emptyList(),
    val country: List<Pair<String, String>>? = emptyList(),
    val sort: List<Pair<String, String>>? = emptyList(),
)

@Serializable
class SearchFilters(
    var type: String? = null,
    var format: String? = null,
    var genres: String? = null,
    var status: String? = null,
    var translation: String? = null,
    var age: String? = null,
    var country: String? = null,
    var tags: String? = null,
    var year: String? = null,
    var items: String? = null,
    var rating: String? = null,
) {
    fun toSegments(): List<String> = listOfNotNull(
        type, format, genres, status, translation, age, country, tags, year, items, rating,
    )
}
