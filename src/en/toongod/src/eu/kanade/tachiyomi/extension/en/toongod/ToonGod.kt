package eu.kanade.tachiyomi.extension.en.toongod

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ToonGod : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    override val mangaSubString = "webtoons"
}
