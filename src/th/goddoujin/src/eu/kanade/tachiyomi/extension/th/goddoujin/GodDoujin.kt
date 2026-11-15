package eu.kanade.tachiyomi.extension.th.goddoujin

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class GodDoujin : MangaThemesia() {

    override val seriesTypeSelector = ".imptdt:contains(ประเภท) a"
}
