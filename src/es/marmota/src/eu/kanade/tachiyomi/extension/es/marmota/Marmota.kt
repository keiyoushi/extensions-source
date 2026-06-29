package eu.kanade.tachiyomi.extension.es.marmota

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Marmota : Madara() {
    override val dateFormat = SimpleDateFormat("d 'de' MMM 'de' yyyy", Locale("es"))
    override val mangaSubString: String = "comic"
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
