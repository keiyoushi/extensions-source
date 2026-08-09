package eu.kanade.tachiyomi.multisrc.grouple

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

// ============================== Search ===============================
@Serializable
class SearchResponse(
    val total: Int,
    val offset: Int,
    val limit: Int,
    val list: List<SearchResponseDetails>,
) {
    @Serializable
    class SearchResponseDetails(
        private val name: String,
        private val picUrl: String? = null,
        private val elementId: ElementInfo,
    ) {
        @Serializable
        class ElementInfo(
            val linkName: String,
        )
        fun toSManga() = SManga.create().apply {
            title = name
            thumbnail_url = picUrl
            url = "/${elementId.linkName}" // old url compatibility
        }
    }
    val hasNextPage: Boolean get() = offset + limit < total
}

// ============================== Filters ===============================
@Serializable
class FiltersData(
    val sortType: List<Pair<String, String>>? = emptyList(),
    val productionStatus: List<Pair<String, String>>? = emptyList(),
    val translationStatus: List<Pair<String, String>>? = emptyList(),
    val searchFilters: List<Pair<String, String>>? = emptyList(),
    val genre: List<Pair<String, String>>? = emptyList(),
    val category: List<Pair<String, String>>? = emptyList(),
    val limitation: List<Pair<String, String>>? = emptyList(),
    val another: List<Pair<String, String>>? = emptyList(),
    val tags: List<Pair<String, String>>? = emptyList(),
    val years: YearsData? = null,
)

@Serializable
class YearsData(
    val min: Int? = null,
    val max: Int? = null,
)

@Serializable
class FiltersAPIResponse(
    val results: List<Elements>? = emptyList(),
) {
    @Serializable
    class Elements(
        val text: String,
        val id: String,
    )
}
