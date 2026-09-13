package eu.kanade.tachiyomi.extension.fr.yaoiscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class YaoiScan : MangaThemesia() {
    override val mangaUrlDirectory = "/catalogue"
    override val seriesStatusSelector = ".status-value"
}
