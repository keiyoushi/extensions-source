package eu.kanade.tachiyomi.extension.en.crowscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class CrowScans : MangaThemesia("Crow Scans", "https://crowscans.xyz", "en") {

    // The site exposes only status, type, order, and genre filters — author and year
    // are stripped because they always return empty results.
    override fun getFilterList(): FilterList = FilterList(
        super.getFilterList().filterNot { it is AuthorFilter || it is YearFilter },
    )

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val extras = buildList {
            val ratingValue = document.selectFirst(".rating .num[itemprop=ratingValue]")?.text()
            if (!ratingValue.isNullOrEmpty()) {
                val ratingCount = document.selectFirst("meta[itemprop=ratingCount]")?.attr("content")
                add(
                    if (!ratingCount.isNullOrEmpty()) "Rating: $ratingValue ($ratingCount votes)" else "Rating: $ratingValue",
                )
            }
            document.selectFirst(".bmc")?.text()
                ?.takeIf(String::isNotEmpty)
                ?.let(::add)
        }
        if (extras.isNotEmpty()) {
            description = listOfNotNull(description?.takeIf(String::isNotEmpty), extras.joinToString("\n"))
                .joinToString("\n\n")
        }
    }
}
