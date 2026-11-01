package eu.kanade.tachiyomi.extension.en.topmanhuafan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import okhttp3.Request
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TopManhuaFan : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)
    override val mangaSubString = "manhua"
    override val imageFetch = Fetch.IMAGE

    override fun chapterListSelector() = "div.wp-manga-chapter"

    // Image hosts refuse the theme's chapter Referer, and images sent with Origin miss the Cloudflare cache
    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(imageFetch.from(page.url))
        .build()
}
