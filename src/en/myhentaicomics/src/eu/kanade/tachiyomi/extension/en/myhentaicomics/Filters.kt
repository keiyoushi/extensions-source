package eu.kanade.tachiyomi.extension.en.myhentaicomics

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

internal open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

// Values are the site's `sorting` query parameter, not the paths of its sort dropdown
internal class SortFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Newest", ""),
            Pair("Most Viewed", "views"),
            Pair("Most Favorited", "favorite"),
            Pair("Most Commented", "comments"),
            Pair("Longest", "amount"),
        ),
    )

// One checkbox per tag, so several can be picked at once: the site's own tag picker
// takes multiples, and a work usually belongs to several categories, and to a studio
// group alongside the Western catch-all.
internal class TagCheckBox(name: String) : Filter.CheckBox(name)

internal class TagCheckBoxGroup(name: String, tags: List<NamedTag>) : Filter.Group<Filter.CheckBox>(name, tags.map { TagCheckBox(it.name) })

// The site's left bar also covers artists and parodies, but there are 10,381 and 1,612
// of them, far past what a filter list can hold, so those two are typed by name.
internal class ArtistFilter : Filter.Text("Artist (exact name)")

internal class ParodyFilter : Filter.Text("Parody (exact name)")

// On by default, so a tapped author or parody chip means that tag, the way picking the
// exact suggestion in the site's picker does. Categories and groups do not need it:
// their chips select the matching checkbox above.
internal class ResolveNamesFilter : Filter.CheckBox("Resolve artist and parody names typed in the search box", true)

@Serializable
internal data class TagOption(
    val id: Long,
    val text: String,
)

@Serializable
internal data class NamedTag(
    val name: String,
    val value: String,
)

@Serializable
internal data class FilterData(
    val categories: List<NamedTag>,
    val groups: List<NamedTag>,
)
