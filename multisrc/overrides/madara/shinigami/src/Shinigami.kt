package eu.kanade.tachiyomi.extension.id.shinigami

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.TimeUnit

class Shinigami : Madara("Shinigami", "https://shinigamitoon.com", "id") {
    // moved from Reaper Scans (id) to Shinigami (id)
    override val id = 3411809758861089969

    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val encodedString = "AAA AaAAAAH QAAAB0 AAAAcA AAAHMAA AA6AAA ALwAAAC8AA " + "AB0AAAAYQA AAGM AAADoAAAAaQAAAH kAAABvAA AAbQAAA  GkAAABvAAAA cgAAAGcAAAAuAAA AZwAAAGk  " + "AAAB0AAAA aAAAAHUAA ABiAAAALgAAAGkAA ABvAAAAL   wAAAHUAAABzA AAAZQAAAHIAAAAtA AAAYQAAAGcA " + "AABlyAtAAAbgA AAHQAAAB6AAAA LwAAAHUAAA  BcAAAAZQ AAAHIAAAAtAAA AYQAAAGcAAABl AAAAbgAA  AHQAAAB6AAAALgAAAG" + "     oAhAntUAABzAA AAbwAAAG4="

    private val tachiUaUrl = Base64.decode(encodedString.replace("\\s".toRegex(), "").replace("DoA", "BoA").replace("GoAhAntU", "GoA").replace("BlyAt", "BlA").replace("BcA", "BzA"), Base64.DEFAULT).toString(Charsets.UTF_32).replace("z", "s")

    private var secChUaMP: List<String>? = null
    private var userAgent: String? = null
    private var checkedUa = false

    private val uaIntercept = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val customUa = preferences.getString(PREF_KEY_CUSTOM_UA, "")
            try {
                if (customUa!!.isNotBlank()) userAgent = customUa

                if (userAgent.isNullOrBlank() && checkedUa.not()) {
                    val uaResponse = chain.proceed(GET(tachiUaUrl))
                    if (uaResponse.isSuccessful) {
                        val parseTachiUa = uaResponse.use { json.decodeFromString<TachiUaResponse>(it.body.string()) }

                        var listUserAgentString = parseTachiUa.desktop + parseTachiUa.mobile

                        listUserAgentString = listUserAgentString!!.filter {
                            listOf("windows", "android").any { filter ->
                                it.contains(filter, ignoreCase = true)
                            }
                        }
                        userAgent = listUserAgentString!!.random()
                        checkedUa = true
                    }
                    uaResponse.close()
                }

                if (userAgent.isNullOrBlank().not()) {
                    secChUaMP = if (userAgent!!.contains("Windows")) {
                        listOf("?0", "Windows")
                    } else {
                        listOf("?1", "Android")
                    }

                    val newRequest = chain.request().newBuilder()
                        .header("User-Agent", userAgent!!.trim())
                        .header("Sec-CH-UA-Mobile", secChUaMP!![0])
                        .header("Sec-CH-UA-Platform", secChUaMP!![1])
                        .removeHeader("X-Requested-With")
                        .build()

                    return chain.proceed(newRequest)
                }
                return chain.proceed(chain.request())
            } catch (e: Exception) {
                throw IOException(e.message)
            }
        }
    }

    @Serializable
    data class TachiUaResponse(
        val desktop: List<String> = emptyList(),
        val mobile: List<String> = emptyList(),
    )

    // disable random ua in ext setting from multisrc (.setRandomUserAgent)
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(uaIntercept)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder {
        val builder = super.headersBuilder()
            .add("Sec-Fetch-Dest", "document")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-Site", "same-origin")
            .add("Upgrade-Insecure-Requests", "1")
            .add("X-Requested-With", "") // added for webview, and removed in interceptor for normal use

        // used to flush tachi custom ua in webview and use system ua instead
        if (userAgent.isNullOrBlank()) builder.removeAll("User-Agent")

        return builder
    }

    override val mangaSubString = "semua-series"

    // Tags are useless as they are just SEO keywords.
    override val mangaDetailsSelectorTag = ""

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

        val postId = script.substringAfter("var post_id = '").substringBefore("'")
        val chapterData = json.decodeFromString<CDT>(
            script.substringAfter("var chapter_data = '").substringBefore("'"),
        )

        val keyMatch = KEY_REGEX.find(deobfuscated)!!.groupValues
        val key = postId + keyMatch[1] + postId + keyMatch[2] + postId
        val salt = chapterData.s.decodeHex()

        val unsaltedCiphertext = Base64.decode(chapterData.ct, Base64.DEFAULT)
        val ciphertext = SALTED + salt + unsaltedCiphertext

        val decrypted = CryptoAES.decrypt(Base64.encodeToString(ciphertext, Base64.DEFAULT), key)
        val data = json.decodeFromString<List<String>>(decrypted)
        return data.mapIndexed { idx, it ->
            Page(idx, document.location(), it)
        }
    }

    // https://stackoverflow.com/a/66614516
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // remove random ua in setting ext from multisrc and use custom one
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val prefCustomUserAgent = EditTextPreference(screen.context).apply {
            key = PREF_KEY_CUSTOM_UA
            title = TITLE_CUSTOM_UA
            summary = (preferences.getString(PREF_KEY_CUSTOM_UA, "")!!.trim() + SUMMARY_STRING_CUSTOM_UA).trim()
            setOnPreferenceChangeListener { _, newValue ->
                val customUa = newValue as String
                preferences.edit().putString(PREF_KEY_CUSTOM_UA, customUa).apply()
                if (customUa.isNullOrBlank()) {
                    Toast.makeText(screen.context, RESTART_APP_STRING, Toast.LENGTH_LONG).show()
                } else {
                    userAgent = null
                }
                summary = (customUa.trim() + SUMMARY_STRING2_CUSTOM_UA).trim()

                true
            }
        }
        screen.addPreference(prefCustomUserAgent)
    }

    companion object {
        const val TITLE_CUSTOM_UA = "Custom User-Agent"
        const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua"
        const val SUMMARY_STRING_CUSTOM_UA = "\n\nBiarkan kosong untuk menggunakan User-Agent secara random"
        const val SUMMARY_STRING2_CUSTOM_UA = "\n\nKosongkan untuk menggunakan User-Agent secara random"

        const val RESTART_APP_STRING = "Restart Tachiyomi untuk menggunakan pengaturan baru."

        private val KEY_REGEX by lazy { Regex("""post_id\s+\+\s+'(.*?)'\s+\+\s+post_id\s+\+\s+'(.*?)'\s+\+\s+post_id""") }
    }
}
