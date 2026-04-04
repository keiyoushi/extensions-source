package eu.kanade.tachiyomi.extension.ko.rawdex

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RawDEX :
    Madara(
        "RawDEX",
        "https://rawdex.net",
        "ko",
        SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
    ) {

    override val mangaDetailsSelectorStatus = "div.summary-heading:has(h5:contains(Status)) + div"
    override val chapterUrlSuffix = ""
    override fun searchMangaSelector() = "div.page-item-detail.manga"
}
