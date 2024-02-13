package eu.kanade.tachiyomi.multisrc.mmrcms

import org.jsoup.nodes.Element
import org.jsoup.select.Elements

object MMRCMSUtils {
    fun guessCover(baseUrl: String, mangaUrl: String, url: String?): String {
        return if (url == null || url.endsWith("no-image.png")) {
            "$baseUrl/uploads/manga/${mangaUrl.substringAfterLast('/')}/cover/cover_250x350.jpg"
        } else {
            url
        }
    }

    fun Element.imgAttr(): String = when {
        hasAttr("data-background-image") -> absUrl("data-background-image")
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    fun Elements.textWithNewlines() = run {
        select("p, br").prepend("\\n")
        text().replace("\\n", "\n").replace("\n ", "\n")
    }
}
