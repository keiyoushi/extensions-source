package eu.kanade.tachiyomi.extension.tr.jellyring

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Jellyring : Madara(
    "Jellyring",
    "https://jellyring.co",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun genresRequest() = popularMangaRequest(1)

    // Derived from CreepyScans
    override fun parseGenres(document: Document): List<Genre> {
        return document.select(".list-unstyled li").mapNotNull { genre ->
            genre.selectFirst("a[href]")?.let {
                val slug = it.attr("href")
                    .split("/")
                    .last(String::isNotEmpty)

                Genre(it.ownText().trim(), slug)
            }
        }
    }
}
