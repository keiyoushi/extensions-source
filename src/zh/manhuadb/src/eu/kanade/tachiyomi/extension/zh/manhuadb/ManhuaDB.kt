package eu.kanade.tachiyomi.extension.zh.manhuadb

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class ManhuaDB : MDB("漫画DB", "https://www.manhuadb.com"), ConfigurableSource {

    override val supportsLatest = false

    override fun listUrl(params: String) = "$baseUrl/manhua/list-$params.html"
    override fun extractParams(listUrl: String) = listUrl.substringAfter("/list-").removeSuffix(".html")
    override fun searchUrl(page: Int, query: String) = "$baseUrl/search?q=$query&p=$page"

    override fun popularMangaNextPageSelector() = "nav > div.form-inline > :nth-last-child(2):not(.disabled)"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used.")

    override val authorSelector = "a.comic-creator"
    override fun transformDescription(description: String) = description.substringBeforeLast("欢迎在漫画DB观看")

    override fun chapterListParse(response: Response) = super.chapterListParse(response).asReversed()

    private val json: Json by injectLazy()

    // https://www.manhuadb.com/assets/js/vg-read.js
    override fun parseImages(imgData: String, readerConfig: Element): List<String> {
        val list: List<Image> = Base64.decode(imgData, Base64.DEFAULT)
            .let { json.decodeFromString(String(it)) }
        val host = readerConfig.attr("data-host")
        val dir = readerConfig.attr("data-img_pre")
        val useWebp = preferences.getBoolean(WEBP_PREF, true)
        return list.map {
            host + dir + if (useWebp && it.img_webp != null) it.img_webp else it.img
        }
    }

    @Serializable
    data class Image(val img: String, val img_webp: String? = null)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = WEBP_PREF
            title = "优先使用 WebP 图片格式"
            summary = "默认开启，可以节省网站流量"
            setDefaultValue(true)
        }.let { screen.addPreference(it) }
    }

    companion object {
        private const val WEBP_PREF = "WEBP"
    }
}
