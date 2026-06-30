package eu.kanade.tachiyomi.extension.id.manhwahana

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Manhwahana : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id"))
    override val mangaSubString = "hana-komik"
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
