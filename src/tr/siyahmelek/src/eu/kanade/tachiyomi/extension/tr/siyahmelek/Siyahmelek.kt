package eu.kanade.tachiyomi.extension.tr.siyahmelek

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Siyahmelek : Madara(
    "Gri Melek",
    "https://grimelek.love",
    "tr",
    SimpleDateFormat("dd MMM yyyy", Locale("tr")),
) {
    // Siyahmelek (tr) -> Gri Melek (tr)
    override val id = 6419959498055001014

    override val mangaSubString = "seri"

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // Skip fake image
    // OK: <div class="page-break no-gaps">
    // NG: <div style="display:none" class="page-break no-gaps">
    override val pageListParseSelector = "div.page-break:not([style*=\"display:\"])"
}
