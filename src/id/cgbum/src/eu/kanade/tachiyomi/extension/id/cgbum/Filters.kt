package eu.kanade.tachiyomi.extension.id.cgbum

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(parameter, value)
    }
}

class SortFilter :
    SelectFilter(
        "Urutkan",
        "sort",
        listOf(
            "Update Terbaru" to "latest",
            "Komik Terbaru" to "newest",
            "Komik Terlama" to "oldest",
            "Paling Banyak Dilihat" to "views",
        ),
        state = 3,
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Semua" to "",
            "Ongoing" to "ongoing",
            "Tamat" to "tamat",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipe",
        "type",
        listOf(
            "Semua" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Eksplisit" to "pornhwa",
        ),
    )

class GenreCheckBox(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<String>) :
    Filter.Group<GenreCheckBox>("Genre", genres.map(::GenreCheckBox)),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter("genres[]", it.name) }
    }
}
