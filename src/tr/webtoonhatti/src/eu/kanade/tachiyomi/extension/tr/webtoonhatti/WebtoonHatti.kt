package eu.kanade.tachiyomi.extension.tr.webtoonhatti

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class WebtoonHatti : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override val mangaSubString = "webtoon"

    // Skip fake image
    // OK: <div class="page-break no-gaps">
    // NG: <div style="display:none" class="page-break no-gaps">
    override val pageListParseSelector = "div.page-break:not([style*=display:]):not([style*=visibility:])"
}
