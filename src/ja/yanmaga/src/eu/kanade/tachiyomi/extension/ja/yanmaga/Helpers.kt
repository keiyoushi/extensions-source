package eu.kanade.tachiyomi.extension.ja.yanmaga

import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private val INSERTED_HTML_REGEX = Regex("""insertAdjacentHTML\(\s*['"]\w+['"],\s*("(?:[^"\\]++|\\.)*+")""")

internal fun Response.parseInsertAdjacentHtml(): Document {
    val html = INSERTED_HTML_REGEX.findAll(body.string()).joinToString("") { it.groupValues[1].parseAs<String>() }
    return Jsoup.parseBodyFragment(html, request.url.toString())
}

internal fun String?.toThumbnail(): String? = this?.toHttpUrlOrNull()?.newBuilder()?.query(null)?.build()?.toString()
