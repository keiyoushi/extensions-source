package eu.kanade.tachiyomi.extension.all.comikey

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class SortFilter(state: Selection = Selection(2, false)) :
    Filter.Sort(
        "Sort by",
        arrayOf("Last updated", "Name", "Popularity", "Chapter count"),
        state,
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val state = this.state ?: return
        val value = buildString {
            if (!state.ascending) {
                append("-")
            }

            when (state.index) {
                0 -> append("updated")
                1 -> append("name")
                2 -> append("views")
                3 -> append("chapters")
            }
        }

        builder.addQueryParameter("order", value)
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Filter by",
        arrayOf("All", "Manga", "Webtoon", "New", "Complete", "Exclusive", "Simulpub"),
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state == 0) {
            return
        }

        builder.addQueryParameter("filter", values[state].lowercase())
    }
}
