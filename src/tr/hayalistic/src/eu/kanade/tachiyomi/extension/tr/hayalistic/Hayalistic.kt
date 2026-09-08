package eu.kanade.tachiyomi.extension.tr.hayalistic

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Hayalistic : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
}
