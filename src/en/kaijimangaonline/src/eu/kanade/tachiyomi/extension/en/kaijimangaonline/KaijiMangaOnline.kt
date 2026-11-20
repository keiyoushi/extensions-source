package eu.kanade.tachiyomi.extension.en.kaijimangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source

@Source
abstract class KaijiMangaOnline : SoloManga() {

    override val mangaTitle = "Kaiji"

    override val chapterSlug = "-chapter-"

    override val chapterRegex = Regex("""/manga/(.+)-chapter-(\d+)/?$""")

    override val imageHosts = listOf("img.kaijimanga.com", "kaijimanga.com/wp-content/uploads")

    // The site hosts four arcs sharing chapter numbers. Offsets keep the arcs
    // apart so a descending sort reproduces the site order: kaiji, datenroku,
    // hakairoku, mokushiroku.
    private val arcOffsets = mapOf(
        "kaiji" to 0f,
        "tobaku-datenroku-kaiji" to -1000f,
        "tobaku-hakairoku-kaiji" to -2000f,
        "tobaku-mokushiroku-kaiji" to -3000f,
    )

    override fun chapterFromMatch(href: String, match: MatchResult): SChapter = SChapter.create().apply {
        val (arc, num) = match.destructured
        val number = num.toFloat()
        setUrlWithoutDomain(href)
        name = if (arc == "kaiji") "Chapter $num" else "${arcTitle(arc)} Chapter $num"
        // Chapter 0 is a prologue the site lists last.
        chapter_number = if (number == 0f) -9999f else (arcOffsets[arc] ?: -4000f) + number
    }

    private fun arcTitle(arc: String): String = arc.split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
