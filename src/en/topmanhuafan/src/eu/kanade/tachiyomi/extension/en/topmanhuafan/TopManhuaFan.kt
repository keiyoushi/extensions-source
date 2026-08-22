package eu.kanade.tachiyomi.extension.en.topmanhuafan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.Request
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TopManhuaFan : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)
    override val mangaSubString = "manhua"

    override fun chapterListSelector() = "div.wp-manga-chapter"

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)
}
