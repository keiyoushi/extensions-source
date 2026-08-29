package eu.kanade.tachiyomi.extension.all.hentailoop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
class SourceFilters(
    val artists: List<SourceFilter>,
    val characters: List<SourceFilter>,
    val circles: List<SourceFilter>,
    val conventions: List<SourceFilter>,
    val genres: List<SourceFilter>,
    val languages: List<SourceFilter>,
    val parodies: List<SourceFilter>,
    val releases: List<SourceFilter>,
    val tags: List<SourceFilter>,
)

@Serializable
class SourceFilter(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
class SearchRequest(
    private val query: String,
    private val filters: List<FilterValue>,
    private val specialFilters: List<SpecialFilter>,
    private val sorting: String,
)

@Serializable
class FilterValue(
    private val name: String,
    private val filterValues: List<String>,
    private val operator: String,
)

@JsonClassDiscriminator("name")
@Serializable
sealed interface SpecialFilter

@SerialName("yearFilter")
@Serializable
class YearFilter(
    private val yearOperator: String,
    private val yearValue: String,
) : SpecialFilter

@SerialName("pagesFilter")
@Serializable
class PagesFilter(
    private val values: PagesValues,
) : SpecialFilter

@Serializable
class PagesValues(
    private val min: Int,
    private val max: Int,
)

@SerialName("checkboxFilter")
@Serializable
class CheckboxFilter(
    private val values: CheckboxValues,
) : SpecialFilter

@Serializable
class CheckboxValues(
    private val purpose: String,
    private val checked: Boolean,
)

@Serializable
class Data<T>(
    val success: Boolean,
    val data: T,
)

@Serializable
class AdvancedSearchResponse(
    val more: Boolean = false,
    val posts: List<String> = emptyList(),

    val message: String? = null,
)

@Serializable
class QuerySearchResponse(
    val posts: List<Post>,
) {
    @Serializable
    class Post(
        val id: Int,
        val title: String,
        val thumb: String,
        val link: String,
    )
}

@Serializable
class SchemaGraph(
    @SerialName("@graph")
    val graph: List<GraphItem>,
) {
    @Serializable
    class GraphItem(
        val datePublished: String? = null,
    ) {
        val date get() = datePublished?.replace(Regex(":(\\d{2})$"), "$1")
    }
}
