package eu.kanade.tachiyomi.extension.th.nekopostco

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NekoPostCo : Madara(
    "NekoPost.co (unoriginal)",
    "https://www.nekopost.co",
    "th",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("th")),
) {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
