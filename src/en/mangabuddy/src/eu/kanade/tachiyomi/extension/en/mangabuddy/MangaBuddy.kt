package eu.kanade.tachiyomi.extension.en.mangabuddy

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class MangaBuddy : MadTheme("MangaBuddy", "https://mangabuddy.com", "en") {
    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        url = url.replace(Regex("(?<=[^:/])//+"), "/")
    }
}
