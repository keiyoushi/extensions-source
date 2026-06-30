package eu.kanade.tachiyomi.extension.tr.webtoonhatti

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class WebtoonHatti : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    override val useNewChapterEndpoint = false

    override val mangaSubString = "webtoon"

    // Skip fake image
    // OK: <div class="page-break no-gaps">
    // NG: <div style="display:none" class="page-break no-gaps">
    override val pageListParseSelector = "div.page-break:not([style*=display:]):not([style*=visibility:])"
}
