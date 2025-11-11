package eu.kanade.tachiyomi.extension.pt.sssscanlator

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.getPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class YomuComics :
    MangaThemesia(
        "Yomu Comics",
        "https://yomucomics.com",
        "pt-BR",
        dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
    ),
    ConfigurableSource {

    // SSSScanlator
    override val id = 1497838059713668619

    private val preferences = getPreferences()

    override val client: OkHttpClient = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .readTimeout(1, TimeUnit.MINUTES)
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .set("Alt-Used", baseUrl.substringAfterLast("/"))
            .set("Accept", "image/avif,image/webp,*/*")
            .set("Accept-Language", "pt-BR,pt;q=0.8,en-US;q=0.5,en;q=0.3")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "same-origin")
            .build()

        page.apply {
            imageUrl = imageUrl?.replace("$JETPACK_CDN/", "")
        }
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        super.chapterListParse(response).reversed()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    companion object {
        val JETPACK_CDN = "i0.wp.com"
    }
}
