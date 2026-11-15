package eu.kanade.tachiyomi.extension.vi.sinhsieusao

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = vals[state].second
}

class KindFilter :
    UriPartFilter(
        "Loại",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Series", "series"),
            Pair("Oneshot", "oneshot"),
            Pair("Album ảnh", "album-anh"),
            Pair("Short Manga", "short-manga"),
        ),
    )

class TagsFilter(tags: List<GenreItem>) :
    Filter.Group<TagTriStateFilter>(
        "Thể loại",
        tags.map { TagTriStateFilter(it.name, it.slug) },
    )

class TagTriStateFilter(name: String, val slug: String) : Filter.TriState(name)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Mới nhất", ""),
            Pair("Phổ biến", "views"),
            Pair("Yêu thích", "likes"),
        ),
    )
