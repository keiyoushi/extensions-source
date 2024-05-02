package eu.kanade.tachiyomi.extension.pt.lscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class LScans : Madara(
    "L Scans",
    "https://lscans.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
