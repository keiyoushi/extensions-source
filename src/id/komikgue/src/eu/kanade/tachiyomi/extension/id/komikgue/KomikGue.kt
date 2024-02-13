package eu.kanade.tachiyomi.extension.id.komikgue

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class KomikGue : Madara(
    "Komik Gue",
    "https://komikgue.pro",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
