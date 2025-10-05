package eu.kanade.tachiyomi.extension.en.asmotoon

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.util.Locale

class Asmotoon : Keyoapp(
    "Asmodeus Scans",
    "https://asmotoon.com",
    "en",
) {
    // filtering novel entries
    override fun popularMangaSelector() = "div:contains(Trending) + div .group.overflow-hidden.grid:not(:has(.capitalize:contains(Novel)))"
    override fun latestUpdatesSelector() = "div.grid > div.group:not(:has(.capitalize:contains(Novel)))"
    override fun searchMangaSelector() = "#searched_series_page > button:not(:has(.capitalize:contains(Novel)))"

    override val descriptionSelector: String = "#expand_content"
    override val genreSelector: String = ".gap-3 .gap-1 a"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            genre = buildList {
                document.selectFirst(typeSelector)?.text()?.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(
                            Locale.ENGLISH,
                        )
                    } else {
                        it.toString()
                    }
                }.let(::add)
                document.select(genreSelector).forEach { add(it.text().removeSuffix(",")) }
            }.joinToString()
        }
    }
}
