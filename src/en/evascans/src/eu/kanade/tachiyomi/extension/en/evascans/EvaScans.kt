package eu.kanade.tachiyomi.extension.en.evascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class EvaScans : MangaThemesia() {
    override val mangaUrlDirectory = "/series"
    override val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)

    // Fix search/listing - site uses custom card layout (div elements, not article)
    override fun searchMangaSelector() = "div.manga-card-v, .listupd .bs .bsx"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        // Handle the custom card layout
        val titleElement = element.selectFirst("h3.card-v-title a") ?: element.selectFirst("a")
        titleElement?.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst(".card-v-cover img")?.imgAttr()
            ?: element.selectFirst("img")?.imgAttr()
    }

    // Fix paid chapter filtering - paid chapters have .locked-badge class
    override fun chapterListSelector(): String = "#chapterlist li:not(:has(.locked-badge))"

    // Fix page reading - site uses custom reader with camelCase ID
    override val pageSelector = "div#readerArea img"

    override val seriesDetailsSelector = ".series-premium-header"
    override val seriesTitleSelector = ".series-title-main"
    override val seriesThumbnailSelector = ".series-poster-premium img, .poster-box img"
    override val seriesGenreSelector = ".series-genres-wrap .gen-tag"
    override val seriesTypeSelector = ".stat-v-box:has(.stat-v-label:containsOwn(Type)) .stat-v-value"
    override val seriesStatusSelector = ".stat-v-box:has(.stat-v-label:containsOwn(Status)) .stat-v-value"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val stats = document.select(".stat-v-box").associate { box ->
            box.selectFirst(".stat-v-label")?.text().orEmpty() to
                box.selectFirst(".stat-v-value")?.text()?.trim().orEmpty()
        }

        val rating = stats["Rating"]?.toFloatOrNull()
        val views = stats["Views"]?.takeIf { it.isNotBlank() }
        val synopsis = document.selectFirst(".synopsis-full")
            ?.select("p")
            ?.joinToString("\n\n") { it.text() }
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(".synopsis-short p")?.text()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val altNames = document.selectFirst(".series-title-alt")?.text()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        description = buildList {
            rating?.takeIf { it > 0 }?.let {
                add("Rating: %.2f/10".format(Locale.ENGLISH, it))
            }
            views?.let { add("Views: $it") }
            synopsis?.let { add("Synopsis: $it") }
            if (altNames.isNotEmpty()) {
                add("Alternative Names:\n" + altNames.joinToString("\n") { "- $it" })
            }
        }.joinToString("\n\n")
    }
}
