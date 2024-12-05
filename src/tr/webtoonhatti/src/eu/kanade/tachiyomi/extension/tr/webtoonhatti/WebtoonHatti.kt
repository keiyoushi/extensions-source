package eu.kanade.tachiyomi.extension.tr.webtoonhatti

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class WebtoonHatti : Madara(
    "Webtoon Hatti",
    "https://webtoonhatti.dev",
    "tr",
    dateFormat = SimpleDateFormat("dd MMMM", Locale("tr")),
) {
    override val useNewChapterEndpoint = false

    // Skip fake image
    // OK: <div class="page-break no-gaps">
    // NG: <div style="display:none" class="page-break no-gaps">
    override val pageListParseSelector = "div.page-break:not([style*=display:]):not([style*=visibility:])"
}
