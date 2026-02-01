package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class XManga : Madara("X-Manga", "https://x-manga.org", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)) {
    // Scan Hentai Menu -> X-Manga
    override val id = 4153742697148883998

    override val useNewChapterEndpoint = true

    override val pageListParseSelector = "div.reading-content img"
}
