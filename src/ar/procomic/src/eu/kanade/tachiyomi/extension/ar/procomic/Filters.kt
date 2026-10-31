package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter<T>(
    name: String,
    private val options: List<Pair<String, T>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter<String?>(
        "النوع",
        listOf(
            "الكل" to null,
            "مانجا" to "manga",
            "مانها (صينية)" to "manhua",
            "مانهوا (كورية)" to "manhwa",
        ),
    )

class SortFilter :
    SelectFilter<String>(
        "الترتيب",
        listOf(
            "الأكثر رواجاً" to "popular",
            "آخر تحديث" to "latest",
            "الأحدث" to "newest",
            "التقييم" to "rating",
            "الأكثر مشاهدة" to "views",
            "أبجدي" to "title",
        ),
    )

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreOption>("التصنيفات", genres.map(::GenreOption)) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}

class GenreOption(genre: GenreDto) : Filter.TriState(genre.displayName) {
    val id = genre.id
}
