package eu.kanade.tachiyomi.extension.id.shinigami

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Shinigami : Madara("Shinigami", "https://shinigami07.com", "id"), ConfigurableSource {
    // moved from Reaper Scans (id) to Shinigami (id)
    override val id = 3411809758861089969

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val mangaSubString = "semua-series"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(super.baseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: ${super.baseUrl}"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
    }

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Sec-Fetch-Dest", "document")
        add("Sec-Fetch-Mode", "navigate")
        add("Sec-Fetch-Site", "same-origin")
        add("Upgrade-Insecure-Requests", "1")
        add("X-Requested-With", randomString((1..20).random())) // added for webview, and removed in interceptor for normal use
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(3)
        .build()

    // Tags are useless as they are just SEO keywords.
    override val mangaDetailsSelectorTag = ""

    override val chapterUrlSelector = "div.chapter-link:not([style~=display:\\snone]) a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst(chapterUrlSelector)!!

        name = urlElement.selectFirst("p.chapter-manhwa-title")?.text()
            ?: urlElement.ownText()
        date_upload = urlElement.selectFirst("span.chapter-release-date > i")?.text()
            .let { parseChapterDate(it) }

        val fixedUrl = urlElement.attr("abs:href")

        setUrlWithoutDomain(fixedUrl)
    }

    // Page list
    @Serializable
    data class CDT(val ct: String, val s: String)

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(chapter_data)")?.data()
            ?: throw Exception("chapter_data script not found")

        val deobfuscated = Deobfuscator.deobfuscateScript(script)
            ?: throw Exception("Unable to deobfuscate chapter_data script")

        val keyMatch = KEY_REGEX.find(deobfuscated)?.groupValues
            ?: throw Exception("Unable to find key")

        val chapterData = json.decodeFromString<CDT>(
            CHAPTER_DATA_REGEX.find(script)?.groupValues?.get(1) ?: throw Exception("Unable to get chapter data"),
        )
        val postId = POST_ID_REGEX.find(script)?.groupValues?.get(1) ?: throw Exception("Unable to get post_id")
        val otherId = OTHER_ID_REGEX.findAll(script).firstOrNull { it.groupValues[1] != "post" }?.groupValues?.get(2) ?: throw Exception("Unable to get other id")
        val key = otherId + keyMatch[1] + postId + keyMatch[2] + postId
        val salt = chapterData.s.decodeHex()

        val unsaltedCiphertext = Base64.decode(chapterData.ct, Base64.DEFAULT)
        val ciphertext = salted + salt + unsaltedCiphertext

        val decrypted = CryptoAES.decrypt(Base64.encodeToString(ciphertext, Base64.DEFAULT), key)
        val data = json.decodeFromString<List<String>>(decrypted)
        return data.mapIndexed { idx, it ->
            Page(idx, document.location(), it)
        }
    }

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    companion object {
        private val KEY_REGEX by lazy { Regex("""_id\s+\+\s+'(.*?)'\s+\+\s+post_id\s+\+\s+'(.*?)'\s+\+\s+post_id""") }
        private val CHAPTER_DATA_REGEX by lazy { Regex("""var chapter_data\s*=\s*'(.*?)'""") }
        private val POST_ID_REGEX by lazy { Regex("""var post_id\s*=\s*'(.*?)'""") }
        private val OTHER_ID_REGEX by lazy { Regex("""var (\w+)_id\s*=\s*'(.*?)'""") }
        private const val RESTART_APP = "Untuk menerapkan perubahan, restart aplikasi."
        private const val BASE_URL_PREF_TITLE = "Ubah Domain"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY = "Untuk penggunaan sementara. Memperbarui aplikasi akan menghapus pengaturan"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }
}
