package eu.kanade.tachiyomi.extension.id.klikmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class KlikManga : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))
    override val mangaSubString = "daftar-komik"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
