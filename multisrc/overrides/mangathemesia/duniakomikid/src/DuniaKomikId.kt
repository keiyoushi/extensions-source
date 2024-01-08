package eu.kanade.tachiyomi.extension.id.duniakomikid

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class DuniaKomikId : MangaThemesia("DuniaKomik.id", "https://duniakomik.org", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id", "ID"))) {

    override val hasProjectPage = true
}
