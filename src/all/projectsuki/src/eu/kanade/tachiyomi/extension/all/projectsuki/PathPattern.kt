package eu.kanade.tachiyomi.extension.all.projectsuki

import okhttp3.HttpUrl

/**
 *  @see EXTENSION_INFO Found in ProjectSuki.kt
 */
@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

/**
 * Utility class made to help identify different urls.
 *
 * null regex means wildcard, matches anything.
 *
 * Meant to be used with [matchAgainst], will match against [HttpUrl.pathSegments]
 *
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
data class PathPattern(val paths: List<Regex?>) {
    constructor(vararg paths: Regex?) : this(paths.asList())

    init {
        if (paths.isEmpty()) {
            reportErrorToUser { "Invalid PathPattern, cannot be empty!" }
        }
    }
}

/**
 * Utility class to represent the [MatchResult]s obtained when matching a [PathPattern]
 * against an [HttpUrl].
 *
 * When [matchResults] is null, it means the [HttpUrl] either:
 *  - when `allowSubPaths` in [matchAgainst] is `false`: [HttpUrl.pathSegments]`.size` != [PathPattern.paths]`.size`
 *  - when `allowSubPaths` in [matchAgainst] is `true`: [HttpUrl.pathSegments]`.size` < [PathPattern.paths]`.size`
 *
 * @see matchAgainst
 *
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
data class PathMatchResult(val doesMatch: Boolean, val matchResults: List<MatchResult?>?) {
    operator fun get(name: String): MatchGroup? = matchResults?.firstNotNullOfOrNull {
        it?.groups
            // this throws if the group by "name" isn't found AND can return null too
            ?.runCatching { get(name) }
            ?.getOrNull()
    }

    init {
        if (matchResults?.isEmpty() == true) {
            reportErrorToUser { "Invalid PathMatchResult, matchResults must either be null or not empty!" }
        }
    }
}

/**
 * @see PathPattern
 * @see PathMatchResult
 */
fun HttpUrl.matchAgainst(pattern: PathPattern, allowSubPaths: Boolean = false, ignoreEmptySegments: Boolean = true): PathMatchResult {
    val actualSegments: List<String> = if (ignoreEmptySegments) pathSegments.filter { it.isNotBlank() } else pathSegments
    val sizeReq = when (allowSubPaths) {
        false -> actualSegments.size == pattern.paths.size
        true -> actualSegments.size >= pattern.paths.size
    }

    if (!sizeReq) return PathMatchResult(false, null)

    val matchResults: MutableList<MatchResult?> = ArrayList()
    var matches = true

    actualSegments.zip(pattern.paths) { segment, regex ->
        val match: MatchResult? = regex?.matchEntire(segment)
        matchResults.add(match)
        matches = matches && (regex == null || match != null)
    }

    return PathMatchResult(matches, matchResults)
}
