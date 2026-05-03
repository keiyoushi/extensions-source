package eu.kanade.tachiyomi.extension.es.mhscans

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MHScans :
    Madara(
        "MHScans",
        "https://mh.inventariooculto.com",
        "es",
        dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
    ),
    ConfigurableSource {
    override val mangaSubString = "series"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 3, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    private val preferences: SharedPreferences = getPreferences()

    override fun chapterListSelector(): String {
        val baseSelector = super.chapterListSelector()
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

        if (!removePremium) {
            return baseSelector
        }

        return "$baseSelector:not(.premium)"
    }

    override fun pageListParse(document: Document): List<Page> {
        super.pageListParse(document).also {
            if (it.isNotEmpty()) return it
        }

        val html = document.html()

        val nonce = NONCE_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("Could not find the security nonce")

        val chapterId = document.selectFirst("#wp-manga-current-chap")?.attr("data-id")
            ?.takeIf { it.isNotEmpty() }
            ?: throw Exception("Could not find chapter ID")
        val mangaId = document.selectFirst("#manga-reading-nav-head")?.attr("data-id")
            ?.takeIf { it.isNotEmpty() }
            ?: throw Exception("Could not find manga ID")

        // Request the token and reader URL
        val ajaxHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val tokenBody = FormBody.Builder()
            .add("action", "rk_get_token")
            .add("nonce", nonce)
            .add("chapter_id", chapterId)
            .add("manga_id", mangaId)
            .build()

        val tokenData = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, tokenBody))
            .execute()
            .parseAs<RkTokenResponse>().data

        // Fetch the decoy reader page with the token
        val readerBody = FormBody.Builder()
            .add("rt", tokenData.token)
            .add("chapter_id", tokenData.chapterId.toString())
            .add("manga_id", tokenData.mangaId.toString())
            .build()

        val readerDocument = client.newCall(POST(tokenData.readerUrl, headers, readerBody)).execute().asJsoup()

        return readerDocument.select("div.rk-page-wrap img, img.rk-img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") })
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos de pago"
            summary = "Oculta automáticamente los capítulos que requieren Taels."
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true

        private val NONCE_REGEX = """"nonce"\s*:\s*"([^"]+)"""".toRegex()
    }
}
