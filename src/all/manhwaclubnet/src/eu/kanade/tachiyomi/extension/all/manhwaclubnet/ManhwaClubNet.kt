package eu.kanade.tachiyomi.extension.all.manhwaclubnet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class ManhwaClubNet : Madara() {
    override val chapterMode = ChapterMode.AdminAjax

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> = super.fetchChapters(mangaPath, id, mangaPage).let { chapters ->
        when (lang) {
            "en" -> chapters.filterNot { it.name.endsWith(" raw") }
            "ko" -> chapters.filter { it.name.endsWith(" raw") }
            else -> emptyList()
        }
    }
}
