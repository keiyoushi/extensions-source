package eu.kanade.tachiyomi.extension.th.doujinza

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DoujinZa : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "doujin"
}
