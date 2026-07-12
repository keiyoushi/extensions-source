package eu.kanade.tachiyomi.extension.en.shojoscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class VioletScans : MangaThemesia() {
    override val mangaUrlDirectory = "/comics"

    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val altNames = document.selectFirst(".alternative .desktop-titles")?.text()
            ?.split(Regex("""\s*[|/]\s*"""))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }

        if (!altNames.isNullOrEmpty()) {
            val baseDescription = description.orEmpty()
                .substringBefore(altNamePrefix)
                .trim()

            description = buildString {
                append(baseDescription)
                if (isNotEmpty()) append("\n\n")
                append(altNamePrefix.trim())
                append("\n")
                altNames.joinTo(this, "\n") { "- $it" }
            }
        }
    }
}
