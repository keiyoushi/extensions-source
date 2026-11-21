package eu.kanade.tachiyomi.extension.en.maidsamamangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class MaidSamaMangaOnline : SoloManga() {

    override val mangaTitle = "Maid Sama"

    private val specialRegex = Regex("""Oneshot Special (\d+)""")

    // Specials come after the last chapter (85.2), without this they'd be read as chapters 1-5
    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        specialRegex.find(name)?.let { chapter_number = 86f + it.groupValues[1].toFloat() / 100f }
    }
}
