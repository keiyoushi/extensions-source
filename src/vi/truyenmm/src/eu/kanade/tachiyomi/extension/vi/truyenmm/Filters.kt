package eu.kanade.tachiyomi.extension.vi.truyenmm

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(data: Map<String, String>?): FilterList = FilterList(
    buildList {
        add(Filter.Header("Khi tìm kiếm bộ lọc sẽ bị bỏ qua"))

        data.orEmpty().takeIf {
            it.isNotEmpty()
        }?.let { add(GenreFilter(it.toList())) }
    },
)

internal class GenreFilter(val genres: List<Pair<String, String>>) :
    Filter.Select<String>(
        "Thể loại",
        genres.map { it.first }.toTypedArray(),
    ) {
    fun toUriPart(): String = genres[state].second
}
