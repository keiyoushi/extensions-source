package eu.kanade.tachiyomi.extension.en.rezoscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class RezoScans : Keyoapp(
    "Rezo Scans",
    "https://rezoscans.com",
    "en",
) {
    override fun pageListParse(document: Document): List<Page> {
        val cdnUrl = getCdnUrl(document)
        return document.select("#pages > img")
            .map { it.attr("uid") }
            .filter(String::isNotEmpty)
            .mapIndexed { index, img ->
                Page(index, document.location(), "$cdnUrl/$img")
            }
    }

    private fun getCdnUrl(document: Document): String {
        val cdnHost = document.select("script")
            .firstOrNull { CDN_HOST.containsMatchIn(it.html()) }
            ?.let { CDN_HOST.find(it.html())?.groups?.get("host")?.value }
            ?: throw Exception("CDN host not found")

        return "https://$cdnHost/uploads"
    }

    companion object {
        val CDN_HOST = """realUrl\s+?=\s+?`[^}]+.(?<host>[^/]+)""".toRegex()
    }
}
