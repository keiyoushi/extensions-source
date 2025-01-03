package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.extension.all.komga.dto.AuthorDto
import eu.kanade.tachiyomi.extension.all.komga.dto.LibraryDto
import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

internal class TypeSelect : Filter.Select<String>(
    "Search for",
    arrayOf(
        Komga.TYPE_SERIES,
        Komga.TYPE_READLISTS,
    ),
)

internal class SeriesSort(selection: Selection? = null) : Filter.Sort(
    "Sort",
    arrayOf("Relevance", "Alphabetically", "Date added", "Date updated", "Random"),
    selection ?: Selection(0, false),
)

internal class UnreadFilter : Filter.CheckBox("Unread", false), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (!state) {
            return
        }

        builder.addQueryParameter("read_status", "UNREAD")
        builder.addQueryParameter("read_status", "IN_PROGRESS")
    }
}

internal class InProgressFilter : Filter.CheckBox("In Progress", false), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (!state) {
            return
        }

        builder.addQueryParameter("read_status", "IN_PROGRESS")
    }
}

internal class ReadFilter : Filter.CheckBox("Read", false), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (!state) {
            return
        }

        builder.addQueryParameter("read_status", "READ")
    }
}

internal class LibraryFilter(
    libraries: List<LibraryDto>,
    defaultLibraries: Set<String>,
) : UriMultiSelectFilter(
    "Libraries",
    "library_id",
    libraries.map {
        UriMultiSelectOption(it.name, it.id).apply {
            state = defaultLibraries.contains(it.id)
        }
    },
)

internal class UriMultiSelectOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

internal open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    genres: List<UriMultiSelectOption>,
) : Filter.Group<UriMultiSelectOption>(name, genres), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val whatToInclude = state.filter { it.state }.map { it.id }

        if (whatToInclude.isNotEmpty()) {
            builder.addQueryParameter(param, whatToInclude.joinToString(","))
        }
    }
}

internal class AuthorFilter(val author: AuthorDto) : Filter.CheckBox(author.name, false)

internal class AuthorGroup(
    role: String,
    authors: List<AuthorFilter>,
) : Filter.Group<AuthorFilter>(role.replaceFirstChar { it.titlecase() }, authors), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val authorToInclude = state.filter { it.state }.map { it.author }

        authorToInclude.forEach {
            builder.addQueryParameter("author", "${it.name},${it.role}")
        }
    }
}

internal class CollectionSelect(
    val collections: List<CollectionFilterEntry>,
) : Filter.Select<String>("Collection", collections.map { it.name }.toTypedArray())

internal data class CollectionFilterEntry(val name: String, val id: String? = null)
