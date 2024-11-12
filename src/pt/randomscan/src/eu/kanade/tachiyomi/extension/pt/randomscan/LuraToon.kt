package eu.kanade.tachiyomi.extension.pt.randomscan

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.Capitulo
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.CapituloPagina
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.Manga
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.getValue

class LuraToon :
    PeachScan(
        "Lura Toon",
        "https://luratoons.com",
        "pt-BR",
    ),
    ConfigurableSource {

    private val json: Json by injectLazy()

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

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/api/obra${manga.url}", headers)
    }
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservable()
            .map { response ->
                chapterListParse(manga, response)
            }
    }
    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString<T>(body.string())
    }

    fun chapterListParse(manga: SManga, response: Response): List<SChapter> {
        val comics = response.parseAs<Manga>()

        return comics.caps.sortedBy {
            -it.num
        }.map { chapterFromElement(manga, it) }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    }

    fun chapterFromElement(manga: SManga, capitulo: Capitulo): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain("/api/obra${manga.url}${capitulo.slug}")
            name = capitulo.slug
            date_upload = runCatching {
                dateFormat.parse(capitulo.data)!!.time
            }.getOrDefault(0L)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val capitulo = response.parseAs<CapituloPagina>()
        val pathSegments = response.request.url.pathSegments
        if (pathSegments.contains("login") || pathSegments.isEmpty()) {
            throw Exception("Faça o login na WebView para acessar o contéudo")
        }
        val files = (0 until capitulo.files).map { i ->
            Page(i, baseUrl, "$baseUrl/api/cap-download/${capitulo.obra.id}/${capitulo.id}/$i")
        }
        return files
    }
}
