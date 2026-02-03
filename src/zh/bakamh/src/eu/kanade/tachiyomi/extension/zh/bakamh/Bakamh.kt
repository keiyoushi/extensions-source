package eu.kanade.tachiyomi.extension.zh.bakamh

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.zh.bakamh.BakamhPreferences.baseUrl
import eu.kanade.tachiyomi.extension.zh.bakamh.BakamhPreferences.preferenceMigration
import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bakamh :
    Madara(
        "巴卡漫画",
        BakamhPreferences.DEFAULT_DOMAIN,
        "zh",
        SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINESE),
    ),
    ConfigurableSource {
    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl by lazy { preferences.baseUrl() }

    override val client = network.cloudflareClient.newBuilder()
        .setRandomUserAgent(UserAgentType.MOBILE)
        .addInterceptor(UserAgentClientHintsInterceptor())
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("Referer", "$baseUrl/")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        BakamhPreferences.buildPreferences(screen.context)
            .forEach(screen::addPreference)
    }

    override val mangaDetailsSelectorStatus = ".post-content_item:contains(状态) .summary-content"
    override fun chapterListSelector() = ".chapter-loveYou a, li:not(.menu-item) a[onclick], li:not(.menu-item) a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url.toString().lowercase()
        return response.asJsoup()
            .select(chapterListSelector())
            .mapNotNull { parseChapter(it, mangaUrl) }
    }

    private fun parseChapter(element: Element, mangaUrl: String): SChapter? {
        // Current URL attribute
        if (element.hasAttr("storage-chapter-url")) {
            return SChapter.create().apply {
                url = element.absUrl("storage-chapter-url")
                name = element.text()
                chapter_number = 0F
            }
        }

        // Compatibility operation for modified versions
        return element.attributes()
            .firstOrNull { attr ->
                val value = attr.value.lowercase()
                value.startsWith(mangaUrl) &&
                    value != mangaUrl && // Not current URL
                    !value.startsWith("$mangaUrl#comment") // Not comment
            }
            ?.let { attr ->
                SChapter.create().apply {
                    url = element.absUrl(attr.key)
                    name = element.text()
                    chapter_number = 0F
                }
            }
    }
}
