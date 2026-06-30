package eu.kanade.tachiyomi.extension.ar.stellarsaber

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class StellarSaber : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("ar"))
}
