package eu.kanade.tachiyomi.extension.fr.yaoiscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class YaoiScan : MangaThemesia() {
    override val mangaUrlDirectory = "/catalogue"
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.FRANCE)
    override val seriesStatusSelector = ".status-value"
}
