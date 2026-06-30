package eu.kanade.tachiyomi.extension.id.klikmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class KlikManga : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))
    override val mangaSubString = "daftar-komik"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
