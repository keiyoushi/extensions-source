package eu.kanade.tachiyomi.extension.ru.mangabuff

import kotlinx.serialization.Serializable

@Serializable
class Dto(
    val content: String,
)

@Serializable
class FiltersData(
    val genres: List<Pair<String, String>>? = emptyList(),
    val tags: List<Pair<String, String>>? = emptyList(),
)
