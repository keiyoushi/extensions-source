package eu.kanade.tachiyomi.extension.id.worldmanhwas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class WorldManhwas : Madara("WorldManhwas", "https://worldmanhwas.zone", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
