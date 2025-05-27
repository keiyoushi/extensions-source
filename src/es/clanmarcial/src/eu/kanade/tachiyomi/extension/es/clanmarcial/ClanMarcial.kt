package eu.kanade.tachiyomi.extension.es.clanmarcial

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ClanMarcial : Madara(
    "Clan Marcial",
    "https://clanmarcial.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint: Boolean = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
