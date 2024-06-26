package eu.kanade.tachiyomi.extension.pt.randomscan

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LuraToon :
    PeachScan(
        "Lura Toon",
        "https://luratoons.com",
        "pt-BR",
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val mangaUrl = element.ownerDocument()!!.location()

        return super.chapterFromElement(element).apply {
            val num = url.removeSuffix("/")
                .substringAfterLast("/")
            val chapUrl = mangaUrl.removeSuffix("/") + "/$num/"

            setUrlWithoutDomain(chapUrl)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val pathSegments = response.request.url.pathSegments
        if (pathSegments.contains("login") || pathSegments.isEmpty()) {
            throw Exception("Faça o login na WebView para acessar o contéudo")
        }
        return super.pageListParse(response)
    }
}
