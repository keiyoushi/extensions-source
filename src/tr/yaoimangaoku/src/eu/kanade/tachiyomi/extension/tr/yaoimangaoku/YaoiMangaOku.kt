package eu.kanade.tachiyomi.extension.tr.yaoimangaoku

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class YaoiMangaOku : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
