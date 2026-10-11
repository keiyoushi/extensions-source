package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Urutkan", sortOptions.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = sortOptions[state]
        builder.addQueryParameter("sortBy", selected.second)
        builder.addQueryParameter("sort", selected.third)
    }

    companion object {
        private val sortOptions = arrayOf(
            Triple("Populer", "popular", "desc"),
            Triple("Terbaru", "updated", "desc"),
            Triple("Judul A-Z", "title", "asc"),
            Triple("Judul Z-A", "title", "desc"),
            Triple("Paling Lama", "updated", "asc"),
        )
    }
}

class TypeFilter :
    Filter.Select<String>("Tipe", typeOptions.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = typeOptions[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter("type[]", value)
        }
    }

    companion object {
        private val typeOptions = arrayOf(
            Pair("Semua", ""),
            Pair("Manga", "MANGA"),
            Pair("Manhwa", "MANHWA"),
            Pair("Manhua", "MANHUA"),
        )
    }
}

class StatusFilter :
    Filter.Select<String>("Status", statusOptions.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = statusOptions[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter("status[]", value)
        }
    }

    companion object {
        private val statusOptions = arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ONGOING"),
            Pair("Completed", "COMPLETED"),
        )
    }
}

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) :
    Filter.Group<Genre>("Genre", genres),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter("genre[]", it.slug)
        }
    }
}

val defaultGenres = listOf(
    Genre("Action", "action"),
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Drama", "drama"),
    Genre("Fantasy", "fantasy"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Mystery", "mystery"),
    Genre("Psychological", "psychological"),
    Genre("Romance", "romance"),
    Genre("School Life", "school-life"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shounen", "shounen"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Supernatural", "supernatural"),
    Genre("Thriller", "thriller"),
)
