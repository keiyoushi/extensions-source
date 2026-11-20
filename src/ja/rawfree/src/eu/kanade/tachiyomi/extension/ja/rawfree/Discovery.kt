package eu.kanade.tachiyomi.extension.ja.rawfree

import eu.kanade.tachiyomi.multisrc.zingtheme.parseApiConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

internal fun discoveredOrigin(document: Document): String {
    val url = document.location().toHttpUrl()
    require(url.isHttps && url.encodedPath == "/") { "Discovery did not reach an HTTPS home page" }
    val config = parseApiConfig(document.select("script").joinToString("\n") { it.data() }, url)
    require(config.ajaxUrl.encodedPath == "/wp-admin/admin-ajax.php") { "Unexpected API endpoint" }
    require(
        document.select("div.entry-ma h2.ma-name a[href]").any {
            val mangaUrl = url.resolve(it.attr("href"))
            mangaUrl != null && mangaUrl.resolve("/") == config.origin && mangaUrl.pathSegments.firstOrNull() == "manga-raw"
        },
    ) { "Could not verify the Raw FREE listing" }
    return config.origin.toString().removeSuffix("/")
}
