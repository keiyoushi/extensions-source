package eu.kanade.tachiyomi.extension.all.sakuramanhwa

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

internal class SortFilter(
    private val sortOptions: List<Pair<String, String>> = listOf(
        "Creation Date" to "create_at",
        "Rating" to "rating",
        "Title" to "title",
        "Type" to "type",
    ),
    defaultSelection: Filter.Sort.Selection = Filter.Sort.Selection(0, false),
) : Filter.Sort(
    name = "Sort By",
    values = sortOptions.map { it.first }.toTypedArray(),
    state = defaultSelection,
) {
    fun setUrlParam(builder: Builder) {
        val selection = state ?: return
        val (_, field) = sortOptions[selection.index]
        builder.setQueryParameter("sort", field)
        builder.setQueryParameter("order", if (selection.ascending) "asc" else "desc")
    }
}

internal class LanguageCheckBoxFilter(name: String, val key: String) : Filter.CheckBox(name)

internal class LanguageCheckBoxFilterGroup(
    data: LinkedHashMap<String, String> = linkedMapOf(
        "Spanish" to "esp",
        "English" to "eng",
        "Chinese" to "ch",
        "Raw" to "raw",
    ),
) : Filter.Group<LanguageCheckBoxFilter>(
    "Language",
    data.map { (k, v) -> LanguageCheckBoxFilter(k, v) },
) {
    fun setUrlParam(builder: Builder) {
        var langParam = false
        state.forEach {
            if (it.state && it.key != "") {
                builder.addQueryParameter("language[]", it.key)
                langParam = true
            }
        }
    }
}

internal class AuthorFilter :
    Filter.Text(
        name = "Author",
        state = "",
    ) {
    fun setUrlParam(builder: Builder) {
        if (state.isNotBlank()) {
            builder.setQueryParameter("authors", state)
        }
    }
}

internal class ArtistFilter :
    Filter.Text(
        name = "Artist",
        state = "",
    ) {
    fun setUrlParam(builder: Builder) {
        if (state.isNotBlank()) {
            builder.setQueryParameter("artists", state)
        }
    }
}

internal class GenreFilter :
    Filter.Text(
        name = "Genre",
        state = "",
    ) {
    fun setUrlParam(builder: Builder) {
        if (state.isNotBlank()) {
            builder.setQueryParameter("genre", state)
        }
    }
}
