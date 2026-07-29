package eu.kanade.tachiyomi.extension.en.gingertoon

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class GingeRTooN : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM.dd.uuuu", Locale.ROOT)
    override val chapterMode = ChapterMode.MangaAjax
}
