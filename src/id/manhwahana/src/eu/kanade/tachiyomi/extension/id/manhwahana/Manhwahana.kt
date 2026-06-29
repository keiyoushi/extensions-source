package eu.kanade.tachiyomi.extension.id.manhwahana

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Manhwahana :
    Madara(
        "Manhwahana",
        "https://manhwahana.com",
        "id",
        dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id")),
    ) {
    override val mangaSubString = "hana-komik"
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
