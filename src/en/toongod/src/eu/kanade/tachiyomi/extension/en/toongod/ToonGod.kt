package eu.kanade.tachiyomi.extension.en.toongod

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ToonGod : Madara() {
    override val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
    override val mangaSubString = "webtoons"
    override val useNewChapterEndpoint = false
}
