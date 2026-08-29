package eu.kanade.tachiyomi.extension.id.komikav

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Apkomik : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))

    override val client = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .add("Referer", "$baseUrl/")
            .add("Sec-GPC", "1")
            .add("Sec-Fetch-Dest", "image")
            .add("Sec-Fetch-Mode", "no-cors")
            .add("Sec-Fetch-Site", "same-origin")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
