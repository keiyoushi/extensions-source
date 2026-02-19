package eu.kanade.tachiyomi.extension.vi.hangtruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val genresFetchAttempts = AtomicInteger(0)
private val genresFetched = AtomicBoolean(false)

@Volatile
private var genresList: List<FilterData> = emptyList()

private val genreRegex = Regex("""\s*#\s*(.*)\s*""")

fun fetchMetadata(baseUrl: String, client: OkHttpClient) {
    if (genresFetchAttempts.get() < 3 && !genresFetched.get()) {
        try {
            client.newCall(GET("$baseUrl/tim-kiem"))
                .execute().asJsoup()
                .let { document ->
                    genresList = document.select(".list-genres span")
                        .mapNotNull {
                            genreRegex.find(it.ownText())
                                ?.groupValues?.getOrNull(1)?.trim()
                                ?.let { name -> FilterData(it.attr("data-value"), name) }
                        }
                    genresFetched.set(true)
                }
        } catch (_: Exception) {
        } finally {
            genresFetchAttempts.incrementAndGet()
        }
    }
}

internal class SortFilter(
    selection: Selection = Selection(0, false),
    private val options: List<SelectFilterOption> = getSortFilter(),
) : Filter.Sort(
    "Sắp xếp",
    options.map { it.name }.toTypedArray(),
    selection,
) {
    val selected: SelectFilterOption
        get() = state?.index?.let { options.getOrNull(it) } ?: options[0]

    fun toUriPart(): String {
        val base = selected.value
        val order = if (state?.ascending == true) "_asc" else "_desc"
        return if (base.isNotEmpty()) base + order else ""
    }
}

private fun getSortFilter() = listOf(
    SelectFilterOption("Liên quan", ""),
    SelectFilterOption("Lượt xem", "view"),
    SelectFilterOption("Ngày cập nhật", "udpated_at_date"),
    SelectFilterOption("Ngày đăng", "created_at_date"),
)

internal class SelectFilterOption(val name: String, val value: String)

internal class GenresFilter(
    genres: List<FilterData> = genresList,
) : UriPartMultiSelectFilter(
    "Genres",
    "genreIds",
    genres.map {
        MultiSelectOption(it.name, it.id)
    },
)

internal class CategoriesFilter(
    categories: List<FilterData> = getCategoriesList(),
) : UriPartMultiSelectFilter(
    "Thể loại",
    "categoryIds",
    categories.map {
        MultiSelectOption(it.name, it.id)
    },
)

private fun getCategoriesList() = listOf(
    FilterData("1", "Manga"),
    FilterData("2", "Manhua"),
    FilterData("3", "Manhwa"),
    FilterData("4", "Marvel Comics"),
    FilterData("5", "DC Comics"),
)

internal class FilterData(
    val id: String,
    val name: String,
)

internal open class MultiSelectOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

internal open class UriPartMultiSelectFilter(
    name: String,
    val param: String,
    genres: List<MultiSelectOption>,
) : Filter.Group<MultiSelectOption>(name, genres),
    UriPartFilter {
    override fun toUriPart(): String {
        val whatToInclude = state.filter { it.state }.map { it.id }

        return if (whatToInclude.isNotEmpty()) {
            whatToInclude.joinToString(",")
        } else {
            ""
        }
    }
}

internal interface UriPartFilter {
    fun toUriPart(): String
}
