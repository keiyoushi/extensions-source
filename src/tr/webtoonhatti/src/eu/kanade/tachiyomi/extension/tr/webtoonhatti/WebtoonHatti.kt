package eu.kanade.tachiyomi.extension.tr.webtoonhatti

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class WebtoonHatti : Madara(
    "Webtoon Hatti",
    "https://webtoonhatti.net",
    "tr",
    dateFormat = SimpleDateFormat("dd MMMM", Locale("tr")),
) {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    // Skip fake image
    // OK: <div class="page-break no-gaps">
    // NG: <div style="display:none" class="page-break no-gaps">
    override val pageListParseSelector = "div.page-break:not([style*=\"display:\"])"
}
