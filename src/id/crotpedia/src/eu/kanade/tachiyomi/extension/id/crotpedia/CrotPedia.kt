package eu.kanade.tachiyomi.extension.id.crotpedia

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class CrotPedia : ZManga() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))

    override val hasProjectPage = false
}
