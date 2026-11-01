package eu.kanade.tachiyomi.extension.vi.luvaholic

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class Genre(
    val name: String,
    val slug: String,
)

class GenreFilter(val genres: List<Genre>) :
    Filter.Select<String>(
        "Thể loại",
        arrayOf("Tất cả") + genres.map { it.name }.toTypedArray(),
    ) {
    val selectedSlug: String?
        get() = genres.getOrNull(state - 1)?.slug
}

class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        arrayOf("Tất cả", "Hoàn thành"),
    ) {
    val selectedValue: String?
        get() = when (state) {
            1 -> "full"
            else -> null
        }
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Mới nhất", "Cũ nhất"),
    ) {
    val selectedValue: String
        get() = when (state) {
            1 -> "old"
            else -> "new"
        }
}

fun parseGenreList(data: JsonElement?): List<Genre> {
    if (data == null) return emptyList()
    return data.jsonArray.mapNotNull { el ->
        val obj = el.jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val slug = obj["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
        Genre(name, slug)
    }
}
