package eu.kanade.tachiyomi.extension.id.komikgan

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import java.text.SimpleDateFormat
import java.util.Locale

class KomikGan : ZManga("KomikGan", "https://komikgan.com", "id", SimpleDateFormat("MMM d, yyyy", Locale("id"))) {

    override val hasProjectPage = true
}
