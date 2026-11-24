package eu.kanade.tachiyomi.multisrc.mangak

import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * "Did you mean" candidates for the MangaK sites.
 *
 * The API has no suggestion endpoint and its author filter is an exact match, so the candidate
 * lists come from the sitemaps (authors and titles), the genre endpoint and the tag search.
 * Matching uses optimal-string-alignment distance: 1 edit up to 5 characters, 2 up to 9,
 * 3 beyond, and nothing above 34 % of the name length.
 */
internal class MangaKSuggestions(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val baseUrl: String,
    private val apiUrl: String,
    sourceId: Long,
) {
    enum class Type(val label: String) {
        AUTHOR("author"),
        TITLE("title"),
        TAG("tag"),
        GENRE("genre"),
    }

    data class Suggestion(val display: String, val type: Type)

    private data class Candidate(val display: String, val type: Type, val keys: List<String>, val weight: Int = 0)

    private data class Scored(val candidate: Candidate, val distance: Int, val lengthDiff: Int, val ratio: Double)

    @Serializable
    private class TagResponse(val data: TagData)

    @Serializable
    private class TagData(
        val items: List<TagItem> = emptyList(),
        val pagination: TagPagination = TagPagination(),
    )

    @Serializable
    private class TagPagination(
        val total: Int = 0,
        @SerialName("has_next") val hasNext: Boolean = false,
    )

    @Serializable
    private class TagItem(
        val name: String,
        val slug: String,
        @SerialName("titles_count") val count: Int = 0,
    ) {
        fun toCandidate() = Candidate(
            display = name,
            type = Type.TAG,
            keys = listOf(name.lowercase(), slug.lowercase(), slug.replace('-', ' ').lowercase()).distinct(),
            weight = count,
        )
    }

    private val dir = File(applicationContext.cacheDir, "source_$sourceId/suggest").apply { mkdirs() }
    private val authorsFile = File(dir, "authors.txt")
    private val titlesFile = File(dir, "titles.txt")
    private val genresFile = File(dir, "genres.txt")

    private val mutex = Mutex()
    private var lastCheck = 0L

    private var authors: List<Candidate>? = null
    private var titles: List<Candidate>? = null
    private var genres: List<Candidate>? = null

    suspend fun suggest(input: String, types: Set<Type>): List<Suggestion> = mutex.withLock {
        val query = input.trim()
        if (query.length < MIN_LENGTH) return@withLock emptyList()

        val candidates = buildList {
            if (Type.TAG in types) addAll(searchTags(query))
            ensureFresh()
            if (Type.AUTHOR in types) addAll(authors())
            if (Type.TITLE in types) addAll(titles())
            if (Type.GENRE in types) addAll(genres())
        }

        val ranked = candidates
            .mapNotNull { score(query, it) }
            .sortedWith(
                compareBy(
                    { it.distance },
                    { -it.candidate.weight },
                    { it.lengthDiff },
                    { it.ratio },
                    { it.candidate.display.length },
                ),
            )

        val best = ranked
            .distinctBy { it.candidate.display.lowercase() to it.candidate.type }
            .take(MAX_SUGGESTIONS)
            .map { Suggestion(it.candidate.display, it.candidate.type) }

        if (best.isEmpty()) return@withLock emptyList()

        best
    }

    private suspend fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (lastCheck != 0L && now - lastCheck < RECHECK_MS) return
        lastCheck = now

        runCatching { refreshSitemaps(now) }
        runCatching { refreshGenres(now) }
    }

    private suspend fun refreshSitemaps(now: Long) {
        val authorsStale = !authorsFile.exists() || now - authorsFile.lastModified() > DAY_MS
        val titlesStale = !titlesFile.exists() || now - titlesFile.lastModified() > DAY_MS
        if (!authorsStale && !titlesStale) return

        val indexPath = "$baseUrl/sitemap_index.xml"
        val index = fetchText(indexPath) ?: return
        val maps = locs(index)

        if (authorsStale) {
            val slugs = maps.filter { it.contains("sitemap_authors") }
                .flatMap { url ->
                    val text = fetchText(url)
                    text?.let(::locs).orEmpty()
                }
                .mapNotNull { it.substringAfter("/authors/", "").takeIf(String::isNotBlank) }
                .distinct()
            if (slugs.isNotEmpty()) {
                authorsFile.writeText(slugs.joinToString("\n"))
                authors = null
            }
        }

        if (titlesStale) {
            val slugs = maps.filter { it.contains("sitemap_titles") }
                .flatMap { url ->
                    val text = fetchText(url)
                    text?.let(::locs).orEmpty()
                }
                .mapNotNull { url ->
                    url.substringBefore('?').substringBefore('#').trimEnd('/')
                        .substringAfterLast('/').takeIf(String::isNotBlank)
                }
                .distinct()
            if (slugs.isNotEmpty()) {
                titlesFile.writeText(slugs.joinToString("\n"))
                titles = null
            }
        }
    }

    /**
     * `/tags?q=` matches from the start of the tag name, so a typo in the first
     * letters ("comdey") returns nothing. Probe shorter prefixes, keeping whatever
     * each one finds, until a probe is broad enough that the intended tag cannot
     * be past the window.
     */
    private suspend fun searchTags(input: String): List<Candidate> {
        val query = input.lowercase()
        val found = LinkedHashMap<String, TagItem>()
        val shortest = maxOf(MIN_LENGTH, query.length - (MAX_TAG_PROBES - 1))

        for (length in query.length downTo shortest) {
            val items = fetchTags(query.take(length))
            items.forEach { found.putIfAbsent(it.slug.lowercase(), it) }
            if (items.size >= TAG_PROBE_WINDOW) break
        }

        return found.values.map { it.toCandidate() }
    }

    private suspend fun fetchTags(prefix: String): List<TagItem> = buildList {
        for (page in 1..TAG_PAGES) {
            val response = runCatching {
                client.get(
                    "$apiUrl/tags".toHttpUrl().newBuilder()
                        .addQueryParameter("q", prefix)
                        .addQueryParameter("page", page.toString())
                        .addQueryParameter("limit", TAG_PAGE_SIZE.toString())
                        .build(),
                    headers,
                    Fetch.XHR,
                ).parseAs<TagResponse>()
            }.getOrNull() ?: break

            addAll(response.data.items)
            if (!response.data.pagination.hasNext) break
        }
    }

    private suspend fun refreshGenres(now: Long) {
        if (genresFile.exists() && now - genresFile.lastModified() <= DAY_MS) return

        val response = runCatching {
            client.get("$apiUrl/genres", headers, Fetch.XHR).parseAs<DataDto<GenreDataDto>>()
        }.getOrNull() ?: return
        val items = response.data.items
        if (items.isEmpty()) return

        genresFile.writeText(items.joinToString("\n") { "${it.name}\t${it.slug}\t${it.titlesCount}" })
        genres = null
    }

    private fun authors(): List<Candidate> = authors ?: loadSlugs(authorsFile, Type.AUTHOR).also { authors = it }

    private fun titles(): List<Candidate> = titles ?: loadSlugs(titlesFile, Type.TITLE).also { titles = it }

    private fun genres(): List<Candidate> = genres ?: loadPairs(genresFile, Type.GENRE).also { genres = it }

    private fun loadSlugs(file: File, type: Type): List<Candidate> = file.takeIf(File::exists)?.readLines().orEmpty().mapNotNull { line ->
        val slug = line.trim()
        if (slug.isEmpty()) return@mapNotNull null
        val display = slug.split('-', '_').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercaseChar() }
        }
        Candidate(
            display = display,
            type = type,
            keys = listOf(slug.lowercase(), slug.replace('-', ' ').replace('_', ' ').lowercase()).distinct(),
        )
    }

    private fun loadPairs(file: File, type: Type): List<Candidate> = readPairs(file).mapNotNull { item ->
        if (item.name.isBlank()) return@mapNotNull null
        Candidate(
            display = item.name,
            type = type,
            keys = listOf(
                item.name.lowercase(),
                item.slug.lowercase(),
                item.slug.replace('-', ' ').lowercase(),
            ).distinct(),
            weight = item.count,
        )
    }

    private fun readPairs(file: File): List<TagItem> = file.takeIf(File::exists)?.readLines().orEmpty().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 2) return@mapNotNull null
        TagItem(parts[0].trim(), parts[1].trim(), parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    private fun score(input: String, candidate: Candidate): Scored? {
        val variants = listOf(
            input.lowercase(),
            input.lowercase().replace('-', ' ').replace('_', ' '),
        ).distinct()
        val max = maxEdits(input.length)

        var best = -1
        for (key in candidate.keys) {
            if (kotlin.math.abs(input.length - key.length) > max) continue
            for (variant in variants) {
                val distance = editDistance(variant, key, max)
                if (distance >= 0 && (best < 0 || distance < best)) best = distance
            }
        }
        if (best < 0) return null

        val ratio = best.toDouble() / maxOf(input.length, candidate.display.length)
        if (ratio > MAX_RATIO) return null

        return Scored(candidate, best, kotlin.math.abs(input.length - candidate.display.length), ratio)
    }

    private fun maxEdits(length: Int): Int = when {
        length <= 5 -> 1
        length <= 9 -> 2
        else -> 3
    }

    /**
     * Optimal string alignment with a cap: returns -1 as soon as the best possible
     * distance exceeds [max]. Keeps three rows instead of a full matrix so a search
     * over ~12k candidates does not allocate megabytes per keystroke.
     */
    private fun editDistance(first: String, second: String, max: Int): Int {
        val a = first.lowercase()
        val b = second.lowercase()
        if (kotlin.math.abs(a.length - b.length) > max) return -1

        var prevPrev = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, prevPrev[j - 2] + 1)
                }
                curr[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > max) return -1

            val spare = prevPrev
            prevPrev = prev
            prev = curr
            curr = spare
        }

        return prev[b.length]
    }

    private suspend fun fetchText(url: String): String? = runCatching {
        client.get(url, headers, Fetch.XHR).use { response ->
            val bytes = response.body.bytes()
            val text = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
            } else {
                bytes.decodeToString()
            }
            text
        }
    }.getOrNull()

    private fun locs(xml: String): List<String> {
        val out = ArrayList<String>(2048)
        var index = 0
        while (true) {
            val start = xml.indexOf("<loc>", index)
            if (start < 0) break
            val end = xml.indexOf("</loc>", start + 5)
            if (end < 0) break
            out.add(xml.substring(start + 5, end).trim())
            index = end + 6
        }
        return out
    }

    companion object {
        private const val MIN_LENGTH = 3
        private const val MAX_SUGGESTIONS = 3
        private const val MAX_RATIO = 0.34
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val RECHECK_MS = 60 * 60 * 1000L
        private const val TAG_PAGES = 3
        private const val TAG_PAGE_SIZE = 100

        // A probe this broad is assumed to contain the intended tag.
        private const val TAG_PROBE_WINDOW = 20
        private const val MAX_TAG_PROBES = 6
    }
}
