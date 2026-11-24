package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.model.Filter

// Filters with the same key are OR-ed
internal interface ConditionFilter {
    val key: String
    val appliesToBooks: Boolean get() = true
    fun alternatives(): List<ConditionDto>
}

internal class TypeSelect :
    Filter.Select<String>(
        "Search for",
        arrayOf(
            Komga.TYPE_SERIES,
            Komga.TYPE_READLISTS,
            Komga.TYPE_BOOKS,
        ),
    )

internal class SeriesSort(selection: Selection? = null) :
    Filter.Sort(
        "Sort",
        arrayOf("Relevance", "Alphabetically", "Date added", "Date updated", "Random"),
        selection ?: Selection(0, true),
    )

internal abstract class ReadStatusFilter(name: String, private val statuses: List<String>) :
    Filter.CheckBox(name, false),
    ConditionFilter {
    override val key = "readStatus"
    override fun alternatives() = if (state) statuses.map { ConditionDto(readStatus = OperatorDto("is", it)) } else emptyList()
}

internal class UnreadFilter : ReadStatusFilter("Unread", listOf("UNREAD", "IN_PROGRESS"))

internal class InProgressFilter : ReadStatusFilter("In Progress", listOf("IN_PROGRESS"))

internal class ReadFilter : ReadStatusFilter("Read", listOf("READ"))

internal class LibraryFilter(
    libraries: List<LibraryDto>,
    defaultLibraries: Set<String>,
) : MultiSelectFilter(
    "Libraries",
    libraries.map {
        MultiSelectOption(it.name, it.id).apply {
            state = defaultLibraries.contains(it.id)
        }
    },
    toCondition = { ConditionDto(libraryId = OperatorDto("is", it)) },
)

internal class MultiSelectOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

internal open class MultiSelectFilter(
    name: String,
    options: List<MultiSelectOption>,
    override val appliesToBooks: Boolean = true,
    private val toCondition: (String) -> ConditionDto,
) : Filter.Group<MultiSelectOption>(name, options),
    ConditionFilter {
    override val key = name
    val selectedIds get() = state.filter { it.state }.map { it.id }
    override fun alternatives() = selectedIds.map(toCondition)
}

internal class AuthorFilter(val author: AuthorDto) : Filter.CheckBox(author.name, false)

internal class AuthorGroup(
    role: String,
    authors: List<AuthorFilter>,
) : Filter.Group<AuthorFilter>(role.replaceFirstChar { it.titlecase() }, authors),
    ConditionFilter {
    override val key = "author"
    override fun alternatives() = state.filter { it.state }.map {
        ConditionDto(author = OperatorDto("is", AuthorMatchDto(it.author.name, it.author.role)))
    }
}

internal class CollectionSelect(
    val collections: List<CollectionFilterEntry>,
) : Filter.Select<String>("Collection", collections.map { it.name }.toTypedArray())

internal data class CollectionFilterEntry(val name: String, val id: String? = null, val ordered: Boolean = false)
