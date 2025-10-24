package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.extension.en.kagane.Kagane.Companion.CONTENT_RATINGS
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray

internal class SortFilter(state: Int = 0) : UriPartFilter(
    "Sort By",
    arrayOf(
        Pair("Relevance", ""),
        Pair("Popular", "avg_views,desc"),
        Pair("Latest", "updated_at"),
        Pair("Latest Descending", "updated_at,desc"),
        Pair("By Name", "series_name"),
        Pair("By Name Descending", "series_name,desc"),
        Pair("Books count", "books_count"),
        Pair("Books count Descending", "books_count,desc"),
        Pair("Created at", "created_at"),
        Pair("Created at Descending", "created_at,desc"),
    ),
    state,
)

internal class ContentRatingFilter(
    defaultRatings: Set<String>,
    ratings: List<FilterData> = CONTENT_RATINGS.map { FilterData(it, it.replaceFirstChar { c -> c.uppercase() }) },
) : JsonMultiSelectFilter(
    "Content Rating",
    "content_rating",
    ratings.map {
        MultiSelectOption(it.name, it.id).apply {
            state = defaultRatings.contains(it.id)
        }
    },
)

internal class SourcesFilter(
    sources: List<FilterData> = SOURCES.map { FilterData(it, it) },
) : JsonMultiSelectFilter(
    "Sources",
    "sources",
    sources.map {
        MultiSelectOption(it.name, it.id)
    },
)

internal class FilterData(
    val id: String,
    val name: String,
)

internal open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
    val selected get() = vals[state].second.takeUnless { it.isEmpty() }
}

internal open class MultiSelectOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

internal open class JsonMultiSelectFilter(
    name: String,
    private val param: String,
    genres: List<MultiSelectOption>,
) : Filter.Group<MultiSelectOption>(name, genres), JsonFilter {
    override fun addToJonObject(builder: JsonObjectBuilder) {
        val whatToInclude = state.filter { it.state }.map { it.id }

        if (whatToInclude.isNotEmpty()) {
            builder.putJsonArray(param) {
                whatToInclude.forEach { add(it) }
            }
        }
    }
}

private interface JsonFilter {
    fun addToJonObject(builder: JsonObjectBuilder)
}

private val SOURCES by lazy {
    listOf(
        "Asura Scans",
        "Comikey",
        "Dark Horse Comics",
        "Day Comics",
        "FAKKU",
        "Flame Comics",
        "Grim Scans",
        "Hive Toons",
        "INKR Comics",
        "J-Novel Club",
        "Kana",
        "Kenscans",
        "Kodansha Comics",
        "Lezhin",
        "Luna Toons",
        "Madarascans",
        "MangaDex",
        "Manta",
        "Nyx Scans",
        "One Peace Books",
        "Others",
        "Pocket Comics",
        "Raven Scans",
        "Reset Scans",
        "Rizz Fables",
        "Rokari Comics",
        "Seven Seas Entertainment",
        "Siren Scans",
        "Square Enix Manga",
        "StoneScape",
        "TOKYOPOP",
        "Tapas",
        "Tappytoon",
        "Temple Scan",
        "Thunderscans",
        "Toomics",
        "UDON Entertainment",
        "VAST Visual",
        "VIZ Media",
        "Vortex Scans",
        "Webcomics",
        "Webtoon",
        "Yen Press",
    )
}
