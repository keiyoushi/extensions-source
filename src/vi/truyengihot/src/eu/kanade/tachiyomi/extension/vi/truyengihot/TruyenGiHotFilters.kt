package eu.kanade.tachiyomi.extension.vi.truyengihot

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val query: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : UriFilter, Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(query, vals[state].second)
    }
}

internal class SortFilter(
    private val vals: Array<Pair<String, String>>,
    state: Selection = Selection(2, false),
) : UriFilter,
    Filter.Sort("Sắp xếp", vals.map { it.first }.toTypedArray(), state) {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter("order_add", vals[state?.index ?: 2].second)
        builder.addQueryParameter(
            "order_by_add",
            if (state?.ascending == true) "ASC" else "DESC",
        )
    }
}

internal class Genre(name: String, val id: String) : Filter.TriState(name)

internal open class GenreGroup(name: String, private val key: String, state: List<Genre>) : Filter.Group<Genre>(name, state), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val incl = mutableListOf<String>()
        val excl = mutableListOf<String>()

        state.forEach {
            when (it.state) {
                TriState.STATE_INCLUDE -> incl.add(it.id)
                TriState.STATE_EXCLUDE -> excl.add(it.id)
                else -> {}
            }
        }

        builder.addQueryParameter("${key}_add", incl.joinToString(","))
        builder.addQueryParameter("${key}_remove", excl.joinToString(","))
    }
}

internal class CategoryFilter(state: Int = 0) : UriPartFilter(
    "Phân loại",
    "type_add",
    arrayOf(
        // The site also has novels and anime.
        Pair("Tất cả", "manga"),
        Pair("Truyện 18+", "audult"),
        Pair("Ngôn tình", "noaudult"),
    ),
    state,
)

internal class PublicationTypeFilter : UriPartFilter(
    "Thể loại",
    "genre_add",
    arrayOf(
        Pair("Tất cả", "0"),
        Pair("Manga", "29"),
        Pair("Manhua", "30"),
        Pair("Manhwa", "31"),
        Pair("Tự sáng tác", "206"),
    ),
)

internal class FormatTypeFilter : UriPartFilter(
    "Format",
    "format_add",
    arrayOf(
        Pair("Tất cả", "0"),
        Pair("R15+", "307"),
        Pair("R16+", "56"),
        Pair("R18+", "128"),
        Pair("R21+", "302"),
    ),
)

internal class MagazineFilter : UriPartFilter(
    "Magazines",
    "magazine_add",
    arrayOf(
        Pair("Tất cả", "0"),
        Pair("DL Site", "215"),
        Pair("kaka*page", "217"),
        Pair("lezh*n", "216"),
        Pair("nav*r", "218"),
    ),
)

internal class StatusFilter : UriPartFilter(
    "Trạng thái",
    "status_add",
    arrayOf(
        Pair("Tất cả", "0"),
        Pair("Full", "1"),
        Pair("Ongoing", "2"),
        Pair("Drop", "3"),
    ),
)

internal class ExplicitFilter : UriPartFilter(
    "Explicit",
    "explicit_add",
    arrayOf(
        Pair("Tất cả", "0"),
        Pair("Ecchi", "21"),
        Pair("Hentai", "73"),
        Pair("Oneshot", "230"),
    ),
)

internal class ScanlatorFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Nhóm dịch", "group_add", vals)

internal class TagFilter(state: List<Genre>) : GenreGroup("Tags", "tag", state)

internal class ThemesFilter(state: List<Genre>) : GenreGroup("Themes", "themes", state)

internal fun getSortItems(): Array<Pair<String, String>> = arrayOf(
    Pair("Mới cập nhật", "last_update"),
    Pair("Lượt xem", "views"),
    Pair("Rating", "rating"),
    Pair("Vote", "vote_c"),
    Pair("Tên A-Z", "name"),
)
