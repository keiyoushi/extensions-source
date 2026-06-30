package eu.kanade.tachiyomi.extension.ar.stellarsaber

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class StellarSaber : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("ar"))
}
