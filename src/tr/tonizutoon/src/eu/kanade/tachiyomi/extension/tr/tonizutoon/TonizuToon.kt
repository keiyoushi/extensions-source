package eu.kanade.tachiyomi.extension.tr.tonizutoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TonizuToon : Madara(
    "TonizuToon",
    "https://tonizu.com",
    "tr",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "#manga-title"

    override val mangaDetailsSelectorAuthor = ".summary-heading:contains(Yazar) ~ .summary-content"

    override val mangaDetailsSelectorStatus = ".summary-heading:contains(Durumu) ~ .summary-content"
}
