package eu.kanade.tachiyomi.extension.tr.koreliscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class KoreliScans : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
}
