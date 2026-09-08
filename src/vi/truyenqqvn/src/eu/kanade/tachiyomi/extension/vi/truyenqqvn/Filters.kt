package eu.kanade.tachiyomi.extension.vi.truyenqqvn

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

open class UriPartFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class ListFilter :
    UriPartFilter(
        "Danh sách",
        arrayOf(
            "Truyện hot" to "/truyen-hot",
            "Truyện mới" to "/truyen-moi",
            "Truyện full" to "/truyen-full",
        ),
    )

@Serializable
class Genre(val name: String, val slug: String)

class GenreFilter(genres: List<Genre>) :
    UriPartFilter(
        "Thể loại",
        buildList {
            add("Tất cả" to "")
            genres.forEach { add(it.name to it.slug) }
        }.toTypedArray(),
    )
