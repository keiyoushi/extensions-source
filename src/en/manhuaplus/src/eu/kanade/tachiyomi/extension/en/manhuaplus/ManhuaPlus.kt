package eu.kanade.tachiyomi.extension.en.manhuaplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class ManhuaPlus : Madara() {
    override val filterNonMangaItems = false
    override val pageListParseSelector = ".read-container img"

    // WordPress.com image hosts reject the app's default UA
    override fun imageRequest(page: Page): Request {
        val request = super.imageRequest(page)
        return if (request.url.host.endsWith(".wordpress.com")) {
            request.newBuilder().header("User-Agent", System.getProperty("http.agent")!!).build()
        } else {
            request
        }
    }
}
