package eu.kanade.tachiyomi.extension.ar.comicverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class ComicVerse : ZeistManga() {

    override fun getChapterFeedUrl(doc: Document, mangaTitle: String) = super.getChapterFeedUrl(doc, doc.selectFirst("[data-label]")?.attr("data-label")!!)

    override val popularMangaSelector = "div.PopularPosts figure"

    override val mangaDetailsSelectorGenres = ".cv-genres-above-story > a[rel=tag]"

    override val pageListSelector = "#cvOriginal"
}
