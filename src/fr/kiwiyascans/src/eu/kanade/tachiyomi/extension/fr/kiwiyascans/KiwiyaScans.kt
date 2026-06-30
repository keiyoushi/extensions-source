package eu.kanade.tachiyomi.extension.fr.kiwiyascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class KiwiyaScans : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    override fun chapterListSelector() = "ul li:has(div.chbox:not(:has(> span.mcl-price-num))):has(div.eph-num)"
}
