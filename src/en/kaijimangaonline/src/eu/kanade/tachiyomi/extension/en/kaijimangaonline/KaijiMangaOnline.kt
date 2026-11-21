package eu.kanade.tachiyomi.extension.en.kaijimangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class KaijiMangaOnline : SoloManga() {

    override val mangaTitle = "Kaiji"

    private val chapterUrlRegex = Regex("""/manga/(.+)-chapter-(\d+)/?$""")

    // Four arcs share chapter numbers, so each arc starts after the older ones end (older arcs are finished)
    private val arcOffsets = mapOf(
        "tobaku-mokushiroku-kaiji" to 0f,
        "tobaku-hakairoku-kaiji" to 38f,
        "tobaku-datenroku-kaiji" to 172f,
        "kaiji" to 303f,
    )

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        val (arc, num) = chapterUrlRegex.find(url)?.destructured ?: return@apply
        val number = num.toFloat()
        // Chapter 0 is a prologue the site lists last.
        chapter_number = if (number == 0f) 0f else arcOffsets[arc]?.plus(number) ?: -1f
    }
}
