package eu.kanade.tachiyomi.extension.ru.acomics

import kotlinx.serialization.Serializable

@Serializable
class Dto(
    val categories: List<Pair<String, String>>? = emptyList(),
    val ageRatings: List<Pair<String, String>>? = emptyList(),
    val type: List<Pair<String, String>>? = emptyList(),
    val subscribe: List<Pair<String, String>>? = emptyList(),
    val status: List<Pair<String, String>>? = emptyList(),
    val sort: List<Pair<String, String>>? = emptyList(),
)
