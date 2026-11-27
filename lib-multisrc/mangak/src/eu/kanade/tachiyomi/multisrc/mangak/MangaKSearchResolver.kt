package eu.kanade.tachiyomi.multisrc.mangak

import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Mihon has no way to tell a chip tap from typing: the author chip opens a global text
 * search and the tag chip only becomes a filter when its name is already in the filter
 * list. Everything else arrives as a plain query, so a tapped tag or author used to
 * search titles instead of filtering.
 *
 * This resolves a whole query that exactly names a filter to that filter. Only an exact,
 * complete match counts, so `south korea trip` is still a text search.
 */
internal class MangaKSearchResolver(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val apiUrl: String,
) {
    data class Target(val parameter: String, val value: String)

    private val resolved = ConcurrentHashMap<String, Target>()
    private val misses = ConcurrentHashMap.newKeySet<String>()
    private val authorTotals = ConcurrentHashMap<String, Int>()

    /** Plain words that only became authors after the text search returned nothing. */
    private val fallbackAuthors = ConcurrentHashMap<String, String>()

    suspend fun resolve(query: String, filters: FilterList): Target? {
        val input = query.trim()
        if (input.length < MIN_LENGTH) return null

        // Local matches depend on the current filter state, so they are not cached.
        matchLocal(input, filters)?.let { return it }

        val key = input.lowercase()
        resolved[key]?.let { return it }
        if (key in misses) return null

        val target = matchTag(input) ?: matchAuthor(input)
        if (target != null) {
            resolved[key] = target
        } else {
            misses.add(key)
        }
        return target
    }

    /** Used for page 2+: keep the author a plain query resolved to on page 1. */
    fun cachedAuthor(query: String): String? = fallbackAuthors[query.trim().lowercase()]

    /**
     * Called only after the text search returned nothing. A one-word name never reaches
     * `matchAuthor` (it would hijack `solo`), but it still has to find `yangchong`.
     */
    suspend fun authorForPlainQuery(query: String): String? {
        val key = query.trim().lowercase()
        fallbackAuthors[key]?.let { return it }

        val slug = query.trim().toSlug()
        if (slug.isEmpty()) return null
        if (authorTotal(slug) == 0) return null

        fallbackAuthors[key] = slug
        return slug
    }

    private fun matchLocal(query: String, filters: FilterList): Target? {
        // An explicit Type/Demographics/Format choice wins over the query.
        filters.firstInstanceOrNull<TypeFilter>()
            ?.takeIf { it.state == 0 }
            ?.valueFor(query)
            ?.let { return Target("type", it) }

        filters.firstInstanceOrNull<DemographicFilter>()
            ?.takeIf { it.state == 0 }
            ?.valueFor(query)
            ?.let { return Target("demographic", it) }

        filters.firstInstanceOrNull<FormatFilter>()
            ?.takeIf { it.state == 0 }
            ?.valueFor(query)
            ?.let { return Target("format", it) }

        // A genre and a tag can share a name ("drama"); the genre is the one the app
        // already treats as a filter, so it wins.
        filters.firstInstanceOrNull<GenreList>()?.state
            ?.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?.let { return Target("genres", it.value) }

        return null
    }

    private suspend fun matchTag(query: String): Target? {
        val response = runCatching {
            client.get(
                "$apiUrl/tags".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("limit", TAG_PAGE_SIZE.toString())
                    .build(),
                headers,
                Fetch.XHR,
            ).parseAs<TagSearchResponse>()
        }.getOrNull() ?: return null

        val slug = query.toSlug()
        return response.data.items
            .filter { it.name.equals(query, ignoreCase = true) || it.slug.equals(slug, ignoreCase = true) }
            .maxByOrNull { it.count }
            ?.let { Target("tag", it.slug) }
    }

    private suspend fun matchAuthor(query: String): Target? {
        // Only names shaped like a display name ("Redice Studio", "Ha:rt", "BLACK-SWAN").
        // A plain word stays a title search until that search comes back empty.
        if (query.all(Char::isLetterOrDigit)) return null

        val whole = query.toSlug()
        if (authorTotal(whole) > 0) return Target("author", whole)

        // "Sung-A, Sunga" is two credits, not one compound slug.
        if (',' !in query) return null

        return query.split(',')
            .map { it.trim() }
            .filter { it.length >= MIN_LENGTH }
            .take(MAX_AUTHOR_PARTS)
            .map { it.toSlug() to authorTotal(it.toSlug()) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
            ?.let { Target("author", it) }
    }

    private suspend fun authorTotal(slug: String): Int {
        authorTotals[slug]?.let { return it }

        // A failed probe means "not an author", so the search still runs as text.
        val response = runCatching {
            client.get(
                "$apiUrl/titles/search".toHttpUrl().newBuilder()
                    .addQueryParameter("author", slug)
                    .addQueryParameter("limit", "1")
                    .build(),
                headers,
                Fetch.XHR,
            ).parseAs<DataDto<SearchDataDto>>()
        }.getOrNull() ?: return 0

        return response.data.pagination.total.also { authorTotals[slug] = it }
    }

    @Serializable
    private class TagSearchResponse(val data: TagSearchData)

    @Serializable
    private class TagSearchData(val items: List<TagSearchItem> = emptyList())

    @Serializable
    private class TagSearchItem(
        val name: String,
        val slug: String,
        @SerialName("titles_count") val count: Int = 0,
    )

    private companion object {
        const val MIN_LENGTH = 3
        const val TAG_PAGE_SIZE = 100
        const val MAX_AUTHOR_PARTS = 3
    }
}
