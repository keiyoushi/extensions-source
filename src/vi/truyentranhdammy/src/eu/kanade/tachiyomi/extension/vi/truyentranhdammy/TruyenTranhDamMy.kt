package eu.kanade.tachiyomi.extension.vi.truyentranhdammy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TruyenTranhDamMy : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("vi"))
    override val chapterMode = ChapterMode.AdminAjax
}
