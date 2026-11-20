package eu.kanade.tachiyomi.extension.id.hatsu

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

fun getFilters(genres: List<GenreDto>?) = FilterList(
    listOfNotNull(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        genres?.let { list -> GenreFilter(list.map { Genre(it.name, it.id) }) },
        ProjectFilter(),
    ),
)

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class SortFilter :
    Filter.Sort("Urutkan", sortOptions.map { it.first }.toTypedArray(), Selection(0, false)),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selection = state ?: return
        builder.addQueryParameter("sortBy", sortOptions[selection.index].second)
        builder.addQueryParameter("sort", if (selection.ascending) "asc" else "desc")
    }

    companion object {
        private val sortOptions = arrayOf(
            "Populer" to "popular",
            "Update" to "updated",
            "Judul" to "title",
            "Baru Ditambah" to "created",
        )
    }
}

open class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) builder.addQueryParameter(parameter, value)
    }
}

class TypeFilter :
    SelectFilter(
        "Tipe",
        "type",
        arrayOf(
            "Semua" to "",
            "Manga" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        arrayOf(
            "Semua" to "",
            "Ongoing" to "ONGOING",
            "Tamat" to "COMPLETED",
        ),
    )

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) :
    Filter.Group<Genre>("Genre", genres),
    UriFilter {
    // the API takes genre IDs, not slugs, and matches titles with any of the checked genres
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { builder.addQueryParameter("genre", it.id) }
    }
}

class ProjectFilter : Filter.Select<String>("Project", arrayOf("Semua", "Project")) {
    val isProject get() = state == 1
}
