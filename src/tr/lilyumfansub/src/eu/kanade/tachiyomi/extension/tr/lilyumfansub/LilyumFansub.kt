package eu.kanade.tachiyomi.extension.tr.lilyumfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LilyumFansub : Madara(
    "LilyumFansub",
    "https://lilyumfansub.com.tr",
    "tr",
    SimpleDateFormat("MMMM dd yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = true
}
