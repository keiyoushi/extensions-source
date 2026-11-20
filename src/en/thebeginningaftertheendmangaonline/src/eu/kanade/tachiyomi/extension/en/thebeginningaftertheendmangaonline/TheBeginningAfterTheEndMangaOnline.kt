package eu.kanade.tachiyomi.extension.en.thebeginningaftertheendmangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class TheBeginningAfterTheEndMangaOnline : SoloManga() {

    override val mangaTitle = "The Beginning After The End"

    override val chapterSlug = "beginning-after-the-end-"

    override val chapterRegex = Regex("""(?:chapter|ch)-(\d+)/?$""")

    override val imageHosts = listOf("img.thebeginningaftertheendmanga.com")

    private val preferredUrlRegex = Regex("""/ch-\d+/?$""")

    override fun chapterListParse(document: Document): List<SChapter> {
        // Deduplicate by chapter number, preferring the "ch-N" slug (has images)
        // over "chapter-N" duplicates.
        val byNumber = mutableMapOf<String, String>()
        document.select("a[href*='$chapterSlug']")
            .map { it.attr("abs:href") }
            .forEach { href ->
                val num = chapterRegex.find(href)?.groupValues?.get(1) ?: return@forEach
                val existing = byNumber[num]
                if (existing == null || urlPriority(href) < urlPriority(existing)) {
                    byNumber[num] = href
                }
            }

        return byNumber.map { (chapterNum, href) ->
            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = "Chapter $chapterNum"
                chapter_number = chapterNum.toFloatOrNull() ?: 0f
            }
        }.sortedByDescending { it.chapter_number }
    }

    // Lower is better: "ch-N" slug wins (has images)
    private fun urlPriority(url: String): Int = if (preferredUrlRegex.containsMatchIn(url)) 0 else 1
}
