package eu.kanade.tachiyomi.extension.id.mgkomik

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://mgkomik.id",
        "id",
        SimpleDateFormat("dd MMM yy", Locale.US),
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    private val encodedString = "AAAAaAAA  AHQAAAG 0AAAAcAAAAHM AAAA6AAA  ALwAAAC8 AAAByAAAAYQAA AHcAQuAZwAA" + "AGkAAAG0AA AAaAAAAHUAAA BiAAAAdQAAAHMAA ABlyAtAAAcgAAAG MAAABvAAAAbgAA AHQAAABlAAAA bgAAAHQAAAAuA  " + "AAAYwAAAG 8AAABtAAAALwAAA GsAAABlyAtAAA aQAAAHkAAABv AAAAdQAAAHMAAAD oAiBuAAAaQAAAC8 AAAB1AAAAcwA" + "  AAGUAAABy AAAALQAAAGE AAABnAAA  AZQAAAG 4AAAG0AAAAe gAAAC8AAABnAAAAaA AAAC0AAABwAAAA YQAAAGcAAABlAA  " + " AAegAAAC8AAAB 1AAAAcwAAAGU AAAByAAAALQA AAGEAAABnA AAAZQAAAG  4AAAG0A AAAegAAAC4A AABtAAAAa QAAAG4AQ " + " uAagAAAHM  AAABvAAAAbg=="

    private val keiListUaUrl = Base64.decode(encodedString.replace("\\s".toRegex(), "").replace("DoAiBu", "BoA").replace("G0A", "B0A").replace("BlyAt", "BlA").replace("AQuA", "AAAAuAAAA"), Base64.DEFAULT).toString(Charsets.UTF_32).replace("z", "s")

    private var secChUaMP: List<String>? = null
    private var userAgent: String? = null
    private var checkedUa = false

    private val uaIntercept = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val customUa = preferences.getString(PREF_KEY_CUSTOM_UA, "")
            try {
                if (customUa!!.isNotBlank()) userAgent = customUa

                if (userAgent.isNullOrBlank() && checkedUa.not()) {
                    val uaResponse = chain.proceed(GET(keiListUaUrl))
                    if (uaResponse.isSuccessful) {
                        val parseTachiUa = uaResponse.use { json.decodeFromString<TachiUaResponse>(it.body.string()) }

                        var listUserAgentString = parseTachiUa.desktop + parseTachiUa.mobile

                        listUserAgentString = listUserAgentString.filter {
                            listOf("windows", "android").any { filter ->
                                it.contains(filter, ignoreCase = true)
                            }
                        }
                        userAgent = listUserAgentString.random()
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

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(uaIntercept)
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

    override val mangaSubString = "komik"

    override fun searchMangaNextPageSelector() = "a.page.larger"

    override val chapterUrlSuffix = ""

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_KEY_CUSTOM_UA
            title = TITLE_CUSTOM_UA
            summary = (preferences.getString(PREF_KEY_CUSTOM_UA, "")!!.trim() + SUMMARY_STRING_CUSTOM_UA).trim()
            setOnPreferenceChangeListener { _, newValue ->
                val customUa = newValue as String
                preferences.edit().putString(PREF_KEY_CUSTOM_UA, customUa).apply()
                if (customUa.isBlank()) {
                    Toast.makeText(screen.context, RESTART_APP_STRING, Toast.LENGTH_LONG).show()
                } else {
                    userAgent = null
                }
                summary = (customUa.trim() + SUMMARY_STRING2_CUSTOM_UA).trim()

                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        const val TITLE_CUSTOM_UA = "Custom User-Agent"
        const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua"
        const val SUMMARY_STRING_CUSTOM_UA = "\n\nBiarkan kosong untuk menggunakan User-Agent secara random" // leave empty to use random UA
        const val SUMMARY_STRING2_CUSTOM_UA = "\n\nKosongkan untuk menggunakan User-Agent secara random" // make it blank/empty to use random UA

        const val RESTART_APP_STRING = "Restart Aplikasi untuk menggunakan pengaturan baru." // restart app to use new settings
    }
}
