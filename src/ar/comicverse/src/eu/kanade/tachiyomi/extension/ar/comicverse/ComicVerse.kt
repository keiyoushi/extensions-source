package eu.kanade.tachiyomi.extension.ar.comicverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import org.jsoup.nodes.Document

class ComicVerse : ZeistManga("Comic Verse", "https://arcomixverse.blogspot.com", "ar") {

    override val chapterFeedRegex = """(?:nPL2?\.run|fetchPosts)\(["'](.*?)["']\)""".toRegex()

    override val scriptSelector = "script"

    override val popularMangaSelector = "div.PopularPosts article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = "h3 > a"

    override fun getChapterFeedUrl(doc: Document): String {
        val script = doc.select(scriptSelector).firstOrNull {
            it.html().contains(chapterFeedRegex)
        }

        val label = script?.let {
            chapterFeedRegex.find(it.html())?.groupValues?.get(1)
        } ?: throw Exception("Failed to find chapter feed")

        return apiUrl(label)
            .addQueryParameter("max-results", MAX_CHAPTER_RESULTS.toString())
            .build().toString()
    }
}
