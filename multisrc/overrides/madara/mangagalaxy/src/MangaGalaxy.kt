package eu.kanade.tachiyomi.extension.en.mangagalaxy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaGalaxy : Madara(
    "Manga Galaxy",
    "https://mangagalaxy.me",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US),
) {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(status) + div.summary-content"
    override val mangaDetailsSelectorDescription = "div.summary-heading:contains(Summary) + div"
}
