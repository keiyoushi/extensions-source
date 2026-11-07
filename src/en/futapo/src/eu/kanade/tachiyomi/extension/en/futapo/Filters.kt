package eu.kanade.tachiyomi.extension.en.futapo

import eu.kanade.tachiyomi.source.model.Filter
import org.jsoup.parser.Parser

class TypeFilter :
    Filter.Select<String>(
        "Type",
        arrayOf("All", "Comics", "Blog posts"),
    ) {
    val endpoint get() = when (state) {
        1 -> "pages"
        2 -> "posts"
        else -> null
    }
}

class TagFilter(private val tags: List<TagDto>) :
    Filter.Select<String>(
        "Tag (blog posts)",
        (listOf("Any") + tags.map { Parser.unescapeEntities(it.name, false) }).toTypedArray(),
    ) {
    val id get() = tags.getOrNull(state - 1)?.id
}
