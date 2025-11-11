package eu.kanade.tachiyomi.extension.en.theblank

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferences
import okhttp3.Interceptor
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class TheBlank :
    Madara(
        "The Blank Scanlation",
        "https://theblank.net",
        "en",
        dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US),
    ),
    ConfigurableSource {

    private val preferences = getPreferences()

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .apply {
            val interceptor = Interceptor { chain ->
                try {
                    chain.proceed(chain.request())
                } catch (e: IOException) {
                    val message = "${e.message} - try the workaround in extension settings"
                    throw IOException(message, e)
                }
            }
            interceptors().add(0, interceptor)
        }
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)

        SwitchPreferenceCompat(screen.context).run {
            title = "[Workaround] Open WebView flags"
            summary = "To work around website restrictions:\n" +
                "1. Tap this to open WebView flags.\n" +
                "2. Search for “XReq” in the new screen.\n" +
                "3. Set this flag to “Enabled”: “WebViewXRequestedWithHeaderControl”.\n" +
                "4. Restart the app.\n" +
                "If the screen fails to open or there’s no such flag, try the steps below."

            setOnPreferenceChangeListener { _, _ ->
                val intent = Intent("com.android.webview.SHOW_DEV_UI").apply {
                    putExtra("fragment-id", 2)
                }
                startActivity(intent)
                false
            }
            screen.addPreference(this)
        }

        SwitchPreferenceCompat(screen.context).run {
            title = "Open “Android System WebView” in Play Store"
            summary = "Tap this and try updating Android System WebView in the Play Store. " +
                "If the flags screen still doesn’t open, tap this, enroll into the beta program " +
                "and update again."

            setOnPreferenceChangeListener { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.webview")
                    setPackage("com.android.vending")
                }
                startActivity(intent)
                false
            }
            screen.addPreference(this)
        }
    }
}

private fun startActivity(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val app = Injekt.get<Application>()
    try {
        app.startActivity(intent)
    } catch (e: Throwable) {
        Log.e("TheBlank", "failed to start activity", e)
        Toast.makeText(app, e.message, Toast.LENGTH_LONG).show()
    }
}
