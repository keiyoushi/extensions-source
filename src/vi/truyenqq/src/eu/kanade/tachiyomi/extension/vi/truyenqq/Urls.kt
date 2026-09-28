package eu.kanade.tachiyomi.extension.vi.truyenqq

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// Older custom-URL preferences can contain /doc-truyen; all site routes start at the origin.
internal fun String.truyenQQOrigin(): String = toHttpUrl().newBuilder()
    .encodedPath("/")
    .query(null)
    .fragment(null)
    .build()
    .toString()
    .removeSuffix("/")

// The slug is shared by current routes, saved legacy routes, and wrongly nested browser links.
internal fun String.truyenQQPath(): String {
    val path = toHttpUrlOrNull()?.encodedPath
        ?: substringBefore('?').substringBefore('#')
    val slug = path.trimEnd('/').substringAfterLast('/')
    return "/truyen-tranh/$slug"
}
