package eu.kanade.tachiyomi.extension.tr.webtoontr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class WebtoonTR : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyy", Locale("tr"))
    override val useNewChapterEndpoint = false
}
