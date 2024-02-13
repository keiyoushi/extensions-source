package eu.kanade.tachiyomi.extension.id.hensekai

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import java.text.SimpleDateFormat
import java.util.Locale

class Hensekai : ZManga("Hensekai", "https://hensekai.com", "id", SimpleDateFormat("MMM d, yyyy", Locale("id"))) {
    override val hasProjectPage = true
}
