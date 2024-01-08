@file:Suppress("CanSealedSubClassBeObject")

package eu.kanade.tachiyomi.extension.all.projectsuki

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

/**
 *  @see EXTENSION_INFO Found in ProjectSuki.kt
 */
@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

internal val newlineRegex = """\R""".toRegex(RegexOption.IGNORE_CASE)

/**
 * Handler for Project Suki's applicable [filters][Filter]
 *
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
@Suppress("NOTHING_TO_INLINE")
object ProjectSukiFilters {

    internal sealed interface ProjectSukiFilter {
        fun HttpUrl.Builder.applyFilter()
        val headers: List<Filter.Header> get() = emptyList()
    }

    private inline fun headers(block: () -> String): List<Filter.Header> = block().split(newlineRegex).map { Filter.Header(it) }

    private suspend inline fun <T> SequenceScope<Filter<*>>.addFilter(filter: T) where T : Filter<*>, T : ProjectSukiFilter {
        yieldAll(filter.headers)
        yield(filter)
    }

    @Suppress("UNUSED_PARAMETER")
    fun headersSequence(preferences: ProjectSukiPreferences): Sequence<Filter.Header> = sequenceOf()

    fun filtersSequence(preferences: ProjectSukiPreferences): Sequence<Filter<*>> = sequence {
        addFilter(SearchModeFilter(preferences.defaultSearchMode()))
        yield(Filter.Separator())
        yield(Filter.Header("All filters below will only work in Full Site mode."))
        addFilter(Origin())
        addFilter(Status())
        yield(Filter.Separator())
        addFilter(Author())
        addFilter(Artist())
    }

    @Suppress("UNUSED_PARAMETER")
    fun footersSequence(preferences: ProjectSukiPreferences): Sequence<Filter.Header> = sequenceOf()

    /** Project Suki requires an extra `adv=1` query parameter when using these filters */
    private inline fun HttpUrl.Builder.ensureAdv(): HttpUrl.Builder = setQueryParameter("adv", "1")

    enum class StatusValue(val display: String, val query: String) {
        ANY("Any", ""),
        ONGOING("Ongoing", "ongoing"),
        COMPLETED("Completed", "completed"),
        HIATUS("Hiatus", "hiatus"),
        CANCELLED("Cancelled", "cancelled"),
        ;

        override fun toString(): String = display

        companion object {
            private val values: Array<StatusValue> = values()
            operator fun get(ordinal: Int): StatusValue = values[ordinal]
        }
    }

    enum class OriginValue(val display: String, val query: String) {
        ANY("Any", ""),
        KOREA("Korea", "kr"),
        CHINA("China", "cn"),
        JAPAN("Japan", "jp"),
        ;

        override fun toString(): String = display

        companion object {
            private val values: Array<OriginValue> = OriginValue.values()
            operator fun get(ordinal: Int): OriginValue = values[ordinal]
        }
    }

    enum class SearchMode(val display: String, val description: SearchMode.() -> String) {
        SMART("Smart", { "Searches for books that have chapters using Unicode ICU Collation and utilities, should work for queries in all languages." }),
        SIMPLE("Simple", { "Ideally the same as $SMART. Necessary for Android API < 24. MIGHT make searches faster. Might be unreliable for non-english characters." }),
        FULL_SITE("Full Site", { "Executes a /search web query on the website. Might return non-relevant results without chapters." }),
        ;

        override fun toString(): String = display

        companion object {
            private val values: Array<SearchMode> = SearchMode.values()
            operator fun get(ordinal: Int): SearchMode = values[ordinal]
        }
    }

    class SearchModeFilter(default: SearchMode) : Filter.Select<SearchMode>("Search Mode", SearchMode.values(), state = default.ordinal), ProjectSukiFilter {
        override val headers: List<Header> = headers {
            """
            See Extensions > Project Suki > Gear icon
            for differences and for how to set the default.
            """.trimIndent()
        }

        override fun HttpUrl.Builder.applyFilter() = Unit
    }

    class Author : Filter.Text("Author"), ProjectSukiFilter {
        override val headers: List<Header> = headers {
            """
            Search by a single author:
            """.trimIndent()
        }

        override fun HttpUrl.Builder.applyFilter() {
            when {
                state.isNotBlank() -> ensureAdv().addQueryParameter("author", state)
            }
        }
    }

    class Artist : Filter.Text("Artist"), ProjectSukiFilter {
        override val headers: List<Header> = headers {
            """
            Search by a single artist:
            """.trimIndent()
        }

        override fun HttpUrl.Builder.applyFilter() {
            when {
                state.isNotBlank() -> ensureAdv().addQueryParameter("artist", state)
            }
        }
    }

    class Status : Filter.Select<StatusValue>("Status", StatusValue.values()), ProjectSukiFilter {
        override fun HttpUrl.Builder.applyFilter() {
            when (val state = StatusValue[state /* ordinal */]) {
                StatusValue.ANY -> {} // default, do nothing
                else -> ensureAdv().addQueryParameter("status", state.query)
            }
        }
    }

    class Origin : Filter.Select<OriginValue>("Origin", OriginValue.values()), ProjectSukiFilter {
        override fun HttpUrl.Builder.applyFilter() {
            when (val state = OriginValue[state /* ordinal */]) {
                OriginValue.ANY -> {} // default, do nothing
                else -> ensureAdv().addQueryParameter("origin", state.query)
            }
        }
    }
}
