package eu.kanade.tachiyomi.extension.id.shirodoujin

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Shirodoujin : ZManga() {
    override val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("id"))

    override val hasProjectPage = true
}
