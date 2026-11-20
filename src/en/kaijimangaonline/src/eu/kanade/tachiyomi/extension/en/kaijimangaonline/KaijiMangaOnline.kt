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

    // Four arcs share chapter numbers, so each arc starts after the older ones end (older arcs are finished)
    private val arcOffsets = mapOf(
        "tobaku-mokushiroku-kaiji" to 0f,
        "tobaku-hakairoku-kaiji" to 38f,
        "tobaku-datenroku-kaiji" to 172f,
        "kaiji" to 303f,
    )

    override fun chapterFromMatch(href: String, match: MatchResult): SChapter = SChapter.create().apply {
        val (arc, num) = match.destructured
        val number = num.toFloat()
        setUrlWithoutDomain(href)
        name = if (arc == "kaiji") "Chapter $num" else "${arcTitle(arc)} Chapter $num"
        // Chapter 0 is a prologue the site lists last.
        chapter_number = if (number == 0f) 0f else arcOffsets[arc]?.plus(number) ?: -1f
    }

    private fun arcTitle(arc: String): String = arc.split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
