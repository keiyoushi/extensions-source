package eu.kanade.tachiyomi.extension.en.manhuaplustop

import kotlinx.serialization.Serializable

@Serializable
class ImageListDto(
    val html: String,
)

@Serializable
class FilterData(
    val genres: List<Pair<String, String>>,
    val chapterCounts: List<Pair<String, String>>,
    val statuses: List<Pair<String, String>>,
    val sexes: List<Pair<String, String>>,
    val sorts: List<Pair<String, String>>,
)
