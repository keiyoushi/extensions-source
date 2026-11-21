package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.model.Filter

// Includes with the same key are OR-ed; excludes are AND-ed
internal interface ConditionFilter {
    val key: String
    val appliesToBooks: Boolean get() = true
    fun includes(): List<ConditionDto>
    fun excludes(): List<ConditionDto> = emptyList()
}

internal class TypeSelect(default: String) : Filter.Select<String>("Search for", Komga.TYPES, Komga.TYPES.indexOf(default).coerceAtLeast(0)) {
    val type get() = values[state]
}

internal class SortFilter(selection: Selection = Selection(0, true)) : Filter.Sort("Sort", SORTS.map { it.name }.toTypedArray(), selection) {
    fun criteria(type: String, hasSearchTerm: Boolean): String? {
        val sort = state ?: return null
        val option = SORTS[sort.index]
        val field = when {
            // Komga only ranks by relevance when there is a search term
            option == SORTS[0] && !hasSearchTerm -> SORTS[1].field(type)
            else -> option.field(type)
        } ?: return null
        return "$field,${if (sort.ascending) "asc" else "desc"}"
    }
}

internal class SortOption(val name: String, val series: String?, val books: String?, val readLists: String?) {
    fun field(type: String) = when (type) {
        Komga.TYPE_BOOKS -> books
        Komga.TYPE_READLISTS -> readLists
        else -> series
    }
}

// Indexes 1 and 3 are Popular and Latest
private val SORTS = listOf(
    SortOption("Relevance", "relevance", "relevance", "relevance"),
    SortOption("Alphabetically", "metadata.titleSort", "metadata.title", "name"),
    SortOption("Date added", "createdDate", "createdDate", "createdDate"),
    SortOption("Date updated", "lastModifiedDate", "lastModifiedDate", "lastModifiedDate"),
    SortOption("Random", "random", null, null),
    SortOption("Release date", "booksMetadata.releaseDate", "metadata.releaseDate", null),
    SortOption("Date read", "readDate", "readProgress.readDate", null),
    SortOption("Folder name", "name", "name", null),
    SortOption("Books count", "booksCount", null, null),
    SortOption("File size", null, "fileSize", null),
    SortOption("Page count", null, "media.pagesCount", null),
)

internal abstract class ReadStatusFilter(name: String, private val statuses: List<String>) :
    Filter.CheckBox(name, false),
    ConditionFilter {
    override val key = "readStatus"
    override fun includes() = if (state) statuses.map { ConditionDto(readStatus = OperatorDto("is", it)) } else emptyList()
}

internal class UnreadFilter : ReadStatusFilter("Unread", listOf("UNREAD", "IN_PROGRESS"))

internal class InProgressFilter : ReadStatusFilter("In Progress", listOf("IN_PROGRESS"))

internal class ReadFilter : ReadStatusFilter("Read", listOf("READ"))

internal class LibraryFilter(
    libraries: List<LibraryDto>,
    defaultLibraries: Set<String>,
) : Filter.Group<LibraryOption>(
    "Libraries",
    libraries.map { library -> LibraryOption(library.name, library.id).apply { state = library.id in defaultLibraries } },
),
    ConditionFilter {
    override val key = name
    val selectedIds get() = state.filter { it.state }.map { it.id }
    override fun includes() = selectedIds.map { ConditionDto(libraryId = OperatorDto("is", it)) }
}

internal class LibraryOption(name: String, val id: String) : Filter.CheckBox(name, false)

internal class TriStateOption(name: String, val include: ConditionDto, val exclude: ConditionDto) : Filter.TriState(name)

internal class TriStateGroup(
    name: String,
    options: List<TriStateOption>,
    override val appliesToBooks: Boolean,
) : Filter.Group<TriStateOption>(name, options),
    ConditionFilter {
    override val key = name
    override fun includes() = state.filter { it.isIncluded() }.map { it.include }
    override fun excludes() = state.filter { it.isExcluded() }.map { it.exclude }
}

internal class TriStateFilter(
    name: String,
    private val include: ConditionDto,
    private val exclude: ConditionDto,
    override val appliesToBooks: Boolean,
) : Filter.TriState(name),
    ConditionFilter {
    override val key = name
    override fun includes() = if (isIncluded()) listOf(include) else emptyList()
    override fun excludes() = if (isExcluded()) listOf(exclude) else emptyList()
}

internal class AuthorFilter(val author: AuthorDto) : Filter.CheckBox(author.name, false)

internal class AuthorGroup(
    role: String,
    authors: List<AuthorFilter>,
) : Filter.Group<AuthorFilter>(role.replaceFirstChar { it.titlecase() }, authors),
    ConditionFilter {
    override val key = "author"
    override fun includes() = state.filter { it.state }.map {
        ConditionDto(author = OperatorDto("is", AuthorMatchDto(it.author.name, it.author.role)))
    }
}

internal class CollectionSelect(
    val collections: List<CollectionFilterEntry>,
) : Filter.Select<String>("Collection", collections.map { it.name }.toTypedArray())

internal class CollectionFilterEntry(val name: String, val id: String? = null, val ordered: Boolean = false)
