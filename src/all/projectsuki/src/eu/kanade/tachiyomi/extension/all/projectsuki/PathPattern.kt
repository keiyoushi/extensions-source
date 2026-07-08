package eu.kanade.tachiyomi.extension.all.projectsuki

import okhttp3.HttpUrl

@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

data class PathPattern(val paths: List<Regex?>) {
    constructor(vararg paths: Regex?) : this(paths.asList())

    init {
        if (paths.isEmpty()) {
            reportErrorToUser { "Invalid PathPattern, cannot be empty!" }
        }
    }
}

data class PathMatchResult(val doesMatch: Boolean, val matchResults: List<MatchResult?>?) {
    fun group(segmentIndex: Int, groupIndex: Int = 1): String? = matchResults?.getOrNull(segmentIndex)?.groups?.get(groupIndex)?.value

    init {
        if (matchResults?.isEmpty() == true) {
            reportErrorToUser { "Invalid PathMatchResult, matchResults must either be null or not empty!" }
        }
    }
}

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
