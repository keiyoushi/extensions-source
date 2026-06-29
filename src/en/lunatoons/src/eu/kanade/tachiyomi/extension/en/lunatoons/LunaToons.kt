package eu.kanade.tachiyomi.extension.en.lunatoons

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class LunaToons :
    Keyoapp(
        "Luna Toons",
        "https://lunatoons.org",
        "en",
    ) {
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        document.select("div:has(h1) a[href*='?genre=']")
            .joinToString { it.attr("title") }
            .takeIf { it.isNotEmpty() }
            ?.let {
                genre = genre?.plus(", $it") ?: it
            }
    }
}
