package eu.kanade.tachiyomi.extension.fr.kiwiyascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KiwiyaScans :
    MangaThemesia(
        "Kiwiya Scans",
        "https://kiwiyascans.com",
        "fr",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
    ) {
    override fun chapterListSelector() = "ul li:has(div.chbox:not(:has(> span.mcl-price-num))):has(div.eph-num)"
}
