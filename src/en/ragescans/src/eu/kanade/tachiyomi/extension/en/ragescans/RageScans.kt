package eu.kanade.tachiyomi.extension.en.ragescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class RageScans : MangaThemesia() {
    override fun chapterListSelector() = "li:has(.chbox .eph-num):not(:has([data-bs-target='#lockedChapterModal']))"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val baseDescription = description
            ?.substringBefore(altNamePrefix)
            ?.trim()
            .orEmpty()

        val altNames = document.selectFirst(seriesAltNameSelector)?.ownText()
            ?.let(::parseAltNames)
            .orEmpty()

        description = buildString {
            append(baseDescription)
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(altNamePrefix.trim())
                append("\n")
                altNames.joinTo(this, "\n") { "- $it" }
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun parseAltNames(raw: String): List<String> {
        val separator = if (ALT_NAME_SLASH_SEMICOLON_REGEX.containsMatchIn(raw)) {
            ALT_NAME_SLASH_SEMICOLON_REGEX
        } else {
            ALT_NAME_COMMA_REGEX
        }

        return raw.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
    }

    companion object {
        private val ALT_NAME_SLASH_SEMICOLON_REGEX = Regex("[/;]")
        private val ALT_NAME_COMMA_REGEX = Regex(",")
    }
}
