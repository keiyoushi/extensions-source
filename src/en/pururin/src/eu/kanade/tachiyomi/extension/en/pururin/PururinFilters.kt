package eu.kanade.tachiyomi.extension.en.pururin

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(
    values: Array<Search.Sort> = Search.Sort.values(),
) : Filter.Select<Search.Sort>("Sort by", values) {
    inline val sort get() = values[state]
}

sealed class TagFilter(
    name: String,
    val id: Int,
) : Filter.TriState(name)

sealed class TagGroup<T : TagFilter>(
    name: String,
    values: List<T>,
) : Filter.Group<T>(name, values)

// TODO: Artist, Circle, Contents, Parody, Character, Convention

class Category(name: String, id: Int) : TagFilter(name, id)

class CategoryGroup(
    values: List<Category> = categories,
) : TagGroup<Category>("Categories", values) {
    companion object {
        private val categories get() = listOf(
            Category("Doujinshi", 13003),
            Category("Manga", 13004),
            Category("Artist CG", 13006),
            Category("Game CG", 13008),
            Category("Artbook", 17783),
            Category("Webtoon", 27939),
        )
    }
}

class TagModeFilter(
    values: Array<Search.TagMode> = Search.TagMode.values(),
) : Filter.Select<Search.TagMode>("Tag mode", values) {
    inline val mode get() = values[state]
}

class PagesFilter(
    name: String,
    default: Int,
    values: Array<Int> = range,
) : Filter.Select<Int>(name, values, default) {
    companion object {
        private val range get() = Array(1001) { it }
    }
}

class PagesGroup(
    values: List<PagesFilter> = minmax,
) : Filter.Group<PagesFilter>("Pages", values) {
    inline val range get() = IntRange(state[0].state, state[1].state).also {
        require(it.first <= it.last) { "'Minimum' cannot exceed 'Maximum'" }
    }

    companion object {
        private val minmax get() = listOf(
            PagesFilter("Minimum", 0),
            PagesFilter("Maximum", 100),
        )
    }
}

inline fun <reified T> List<Filter<*>>.find() = find { it is T } as T
