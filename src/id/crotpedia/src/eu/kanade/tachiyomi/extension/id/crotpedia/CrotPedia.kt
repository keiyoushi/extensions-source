package eu.kanade.tachiyomi.extension.id.crotpedia

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class CrotPedia : ZManga() {
    override val dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale("id"))

    override val hasProjectPage = false
}
