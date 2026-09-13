package eu.kanade.tachiyomi.extension.en.evascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

@Source
abstract class EvaScans : MangaThemesia() {
    override val mangaUrlDirectory = "/series"

    override val datePattern = "yyyy/MM/dd"

    override fun searchMangaSelector() = "div.manga-card-v, .listupd .bs .bsx"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val titleElement = element.selectFirst("h3.card-v-title a") ?: element.selectFirst("a")
        titleElement?.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst(".card-v-cover img")?.imgAttr()
            ?: element.selectFirst("img")?.imgAttr()
    }

    override fun chapterListSelector(): String = "#chapterlist li:not(:has(.locked-badge))"

    override val pageSelector = "div#readerArea img"

    override val seriesDetailsSelector = ".series-premium-header"
    override val seriesTitleSelector = ".series-title-main"
    override val seriesThumbnailSelector = ".series-poster-premium img, .poster-box img"
    override val seriesGenreSelector = ".series-genres-wrap .gen-tag"
    override val seriesTypeSelector = ".stat-v-box:has(.stat-v-label:containsOwn(Type)) .stat-v-value"
    override val seriesStatusSelector = ".stat-v-box:has(.stat-v-label:containsOwn(Status)) .stat-v-value"

    override val seriesAltNameSelector = ".series-title-alt"
    override val seriesDescriptionSelector = ".synopsis-full"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val stats = document.select(".stat-v-box").associate { box ->
            box.selectFirst(".stat-v-label")?.text().orEmpty() to
                box.selectFirst(".stat-v-value")?.text()?.trim().orEmpty()
        }

        val rating = stats["Rating"]?.toFloatOrNull()
        val views = stats["Views"]?.takeIf { it.isNotBlank() }

        description = buildList {
            rating?.takeIf { it > 0 }?.let {
                add("Rating: %.2f/10".format(Locale.ENGLISH, it))
            }
            views?.let { add("Views: $it") }
            add("Synopsis: $description")
        }.joinToString("\n\n")
    }
}
