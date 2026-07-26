package eu.kanade.tachiyomi.extension.vi.meosua

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
internal class GenreOption(
    val name: String,
    val path: String,
)

internal class GenreFilter(genres: List<GenreOption>) : Filter.Select<String>("Thể loại", arrayOf("Tất cả", *genres.map { it.name }.toTypedArray())) {
    private val paths = listOf<String?>(null) + genres.map { it.path }

    fun selectedPath(): String? = paths.getOrNull(state)
}
