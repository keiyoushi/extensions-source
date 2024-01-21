package eu.kanade.tachiyomi.extension.all.pururin

import eu.kanade.tachiyomi.source.model.Filter

sealed class TagFilter(
    name: String,
    val id: String,
) : Filter.TriState(name)

sealed class TagGroup<T : TagFilter>(
    name: String,
    values: List<T>,
) : Filter.Group<T>(name, values)

class Category(name: String, id: String) : TagFilter(name, id)

class CategoryGroup(
    values: List<Category> = categories,
) : TagGroup<Category>("Categories", values) {
    companion object {
        private val categories get() = listOf(
            Category("Doujinshi", "{\"id\":13003,\"name\":\"Doujinshi [Category]\"}"),
            Category("Manga", "{\"id\":13004,\"name\":\"Manga [Category]\"}"),
            Category("Artist CG", "{\"id\":13006,\"name\":\"Artist CG [Category]\"}"),
            Category("Game CG", "{\"id\":13008,\"name\":\"Game CG [Category]\"}"),
            Category("Artbook", "{\"id\":17783,\"name\":\"Artbook [Category]\"}"),
            Category("Webtoon", "{\"id\":27939,\"name\":\"Webtoon [Category]\"}"),
        )
    }
}

class PagesFilter(
    name: String,
    default: Int,
    values: Array<Int> = range,
) : Filter.Select<Int>(name, values, default) {
    companion object {
        private val range get() = Array(301) { it }
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
            PagesFilter("Maximum", 300),
        )
    }
}

inline fun <reified T> List<Filter<*>>.find() = find { it is T } as T
