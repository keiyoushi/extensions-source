package eu.kanade.tachiyomi.extension.fr.kiwiyascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class KiwiyaScans : MangaThemesia() {
    override fun chapterListSelector() = "ul li:has(div.chbox:not(:has(> span.mcl-price-num))):has(div.eph-num)"
}
