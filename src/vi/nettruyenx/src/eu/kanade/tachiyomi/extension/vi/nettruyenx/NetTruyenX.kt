package eu.kanade.tachiyomi.extension.vi.nettruyenx

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class NetTruyenX : WPComics(
    "NetTruyenX (unoriginal)",
    "https://nettruyenx.net",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
    gmtOffset = null,
) {
    override val popularPath = "truyen-tranh-hot"

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                val otherName = info.select("h2.other-name").text()
                description = info.select("div.detail-content div div:nth-child(4)").text() +
                    if (otherName.isNotBlank()) "\n\n ${intl["OTHER_NAME"]}: $otherName" else ""
                thumbnail_url = imageOrNull(info.select("div.col-image img").first()!!)
            }
        }
    }
}
