package eu.kanade.tachiyomi.extension.en.drakescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class DrakeScans : MangaThemesia(
    "Drake Scans",
    "https://drakecomic.org",
    "en",
) {
    // madara -> mangathemesia
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(baseUrl.toHttpUrl(), 3)
        .build()

    override fun imageRequest(page: Page): Request {
        val newUrl = page.imageUrl!!.replace(JETPACK_CDN_REGEX, "https://")
        return super.imageRequest(page).newBuilder()
            .url(newUrl)
            .build()
    }

    companion object {
        val JETPACK_CDN_REGEX = """^https:\/\/i[0-9]\.wp\.com\/""".toRegex()
    }
}
