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
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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

    override fun pageListRequest(chapter: SChapter): Request {
        val initialRequest = super.pageListRequest(chapter)
        val response = client.newCall(initialRequest).execute()
        val document = response.asJsoup()

        // Si la página original ya tiene las imágenes, no hacemos el redireccionamiento señuelo
        if (document.select("div.page-break img").isNotEmpty()) {
            return initialRequest
        }

        val html = document.html()

        // Extraemos el nonce inyectado en el script
        val nonce = NONCE_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("Could not find the security nonce")

        val chapterId = document.select(".wp-manga-action-button[data-chapter]").attr("data-chapter").ifEmpty {
            document.select("#wp-manga-chapter-id").attr("value")
        }.ifEmpty {
            document.select("link[rel=shortlink]").attr("href").substringAfter("p=").substringBefore("&")
        }.ifEmpty {
            CHAPTER_ID_REGEX.find(html)?.groupValues?.get(1) ?: ""
        }.takeIf { it.isNotEmpty() } ?: throw Exception("Could not find chapter ID")

        val mangaId = document.select(".wp-manga-action-button[data-post]").attr("data-post").ifEmpty {
            document.select("#wp-manga-current-manga").attr("value")
        }.ifEmpty {
            MANGA_ID_REGEX.find(html)?.groupValues?.get(1) ?: ""
        }.takeIf { it.isNotEmpty() } ?: throw Exception("Could not find manga ID")

        // Extraemos el slug del capítulo de la URL
        val chapterSlug = chapter.url.trimEnd('/').substringAfterLast('/')

        // 0. Registramos la vista (requerido antes de pedir el token)
        val ajaxHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val viewsBody = FormBody.Builder()
            .add("action", "manga_views")
            .add("manga", mangaId)
            .add("chapter", chapterSlug)
            .build()

        client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, viewsBody)).execute().close()

        // 1. Pedimos el Token y la URL destino
        val tokenBody = FormBody.Builder()
            .add("action", "rk_get_token")
            .add("nonce", nonce)
            .add("chapter_id", chapterId)
            .add("manga_id", mangaId)
            .build()

        val tokenResponse = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, tokenBody)).execute()
        val tokenResponseBody = tokenResponse.body.string()

        val tokenData = try {
            tokenResponseBody.parseAs<RkTokenResponse>().data
        } catch (e: Exception) {
            throw Exception("Failed to get reader token (chapter=$chapterId, manga=$mangaId): $tokenResponseBody")
        }

        // 2. Construimos la petición a la página señuelo
        val readerBody = FormBody.Builder()
            .add("rt", tokenData.token)
            .add("chapter_id", tokenData.chapterId.toString())
            .add("manga_id", tokenData.mangaId.toString())
            .build()

        return POST(tokenData.readerUrl, headers, readerBody)
    }

    override fun pageListParse(document: Document): List<Page> {
        // Extraemos desde el lector señuelo
        val pages = document.select("div.rk-page-wrap img, img.rk-img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") })
        }

        if (pages.isNotEmpty()) {
            return pages
        }

        // Fallback al Madara original
        return super.pageListParse(document)
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
        private val CHAPTER_ID_REGEX = """chapter_id["']?\s*[:=]\s*["']?(\d+)""".toRegex()
        private val MANGA_ID_REGEX = """manga_id["']?\s*[:=]\s*["']?(\d+)""".toRegex()
    }
}
