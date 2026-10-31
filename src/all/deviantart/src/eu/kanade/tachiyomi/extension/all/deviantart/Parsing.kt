package eu.kanade.tachiyomi.extension.all.deviantart

import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

private val GALLERY_URL_REGEX = Regex("""/([\w-]+)/gallery(?:/(all|\d+)(?:/[^/]+)?)?/?""")

internal fun HttpUrl.toGalleryPath(baseUrl: String): String? {
    if (host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
    val groups = GALLERY_URL_REGEX.matchEntire(encodedPath)?.groupValues ?: return null
    val folderId = groups[2].ifEmpty { "all" }
    return "/${groups[1]}/gallery/$folderId"
}

internal fun Document.parsePages(deviationId: String): List<Page> = buildList {
    add(createImagePage(0, selectFirst("img[fetchpriority=high]")?.absUrl("src")))
    if (selectFirst("[draggable=false]") == null) return@buildList

    val state = selectFirst("script:containsData(window.__INITIAL_STATE__)")!!.data()
        .substringAfter("window.__INITIAL_STATE__ = JSON.parse(")
        .substringBefore('\n').trim().removeSuffix(");")
        .replace("\\'", "'")
        .parseAs<String>()
        .parseAs<InitialState>()

    state.entities.deviationExtended.getValue(deviationId).additionalMedia.forEachIndexed { i, media ->
        add(createImagePage(i + 1, media.media.imageUrl()))
    }
}
