package eu.kanade.tachiyomi.extension.es.tecnoprojects

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TecnoProjects : Madara(
    "TecnoProjects",
    "https://tecnoprojects.com",
    "es",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
