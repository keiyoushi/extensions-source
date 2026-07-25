package eu.kanade.tachiyomi.extension.id.maidmanga

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MaidManga : ZManga() {
    override val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale("id"))

    override val hasProjectPage = true
}
