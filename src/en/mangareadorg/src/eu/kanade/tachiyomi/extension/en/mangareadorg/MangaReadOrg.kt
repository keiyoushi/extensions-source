package eu.kanade.tachiyomi.extension.en.mangareadorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaReadOrg : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyy", Locale.US)

    override val chapterMode = ChapterMode.MangaAjax
}
