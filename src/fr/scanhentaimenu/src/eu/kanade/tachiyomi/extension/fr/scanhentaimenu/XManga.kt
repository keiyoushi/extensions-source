package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class XManga : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)

    override val useNewChapterEndpoint = true

    override val pageListParseSelector = "div.reading-content img"
}
