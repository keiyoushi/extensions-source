package eu.kanade.tachiyomi.extension.pt.randomscan

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.Capitulo
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.CapituloPagina
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.MainPage
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.Manga
import eu.kanade.tachiyomi.extension.pt.randomscan.dto.SearchResponse
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.getValue

class LuraToon : HttpSource(), ConfigurableSource {
    override val baseUrl = "https://luratoons.net"
    override val name = "Lura Toon"
    override val lang = "pt-BR"
    override val supportsLatest = true
    override val versionId = 2

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = network.cloudflareClient
        .newBuilder()
        .addInterceptor(::loggedVerifyInterceptor)
        .addInterceptor(LuraZipInterceptor()::zipImageInterceptor)
        .rateLimit(3)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .build()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/main/?part=${page - 1}", headers)
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/main/?part=${page - 1}", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/api/autocomplete/$query", headers)
    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/api/obra/${manga.url.trimStart('/')}", headers)
    override fun mangaDetailsRequest(manga: SManga) = chapterListRequest(manga)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val data = response.parseAs<Manga>()
        title = data.titulo
        author = data.autor
        artist = data.artista
        genre = data.generos.joinToString(", ") { it.name }
        status = when (data.status) {
            "Em Lançamento" -> SManga.ONGOING
            "Finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = "$baseUrl${data.capa}"

        val category = data.tipo
        val synopsis = data.sinopse
        description = "Tipo: $category\n\n$synopsis"
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString<T>(body.string())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.parseAs<MainPage>()

        val mangas = document.lancamentos.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$baseUrl${it.capa}"
                setUrlWithoutDomain("/${it.slug}/")
            }
        }

        return MangasPage(mangas, document.lancamentos.isNotEmpty())
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservable()
            .map { response ->
                chapterListParse(manga, response)
            }
    }

    fun chapterListParse(manga: SManga, response: Response): List<SChapter> {
        if (response.code == 404) {
            throw Exception("Capitulos não encontrados, tente migrar o manga, alguns nomes da LuraToon mudaram")
        }

        val comics = response.parseAs<Manga>()

        return comics.caps.sortedByDescending {
            it.num
        }.map { chapterFromElement(manga, it) }
    }

    private fun chapterFromElement(manga: SManga, capitulo: Capitulo) = SChapter.create().apply {
        val capSlug = capitulo.slug.trimStart('/')
        val mangaUrl = manga.url.trimEnd('/').trimStart('/')
        setUrlWithoutDomain("/api/obra/$mangaUrl/$capSlug")
        name = capitulo.num.toString().removeSuffix(".0")
        date_upload = runCatching {
            dateFormat.parse(capitulo.data)!!.time
        }.getOrDefault(0L)
    }

    override fun pageListParse(response: Response): List<Page> {
        val capitulo = response.parseAs<CapituloPagina>()
        val pathSegments = response.request.url.pathSegments
        return (0 until capitulo.files).map { i ->
            Page(i, baseUrl, "$baseUrl/api/cap-download/${capitulo.obra.id}/${capitulo.id}/$i?obra_id=${capitulo.obra.id}&cap_id=${capitulo.id}&slug=${pathSegments[2]}&cap_slug=${pathSegments[3]}")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<SearchResponse>().obras.map {
            SManga.create().apply {
                title = it.titulo
                thumbnail_url = "$baseUrl${it.capa}"
                setUrlWithoutDomain("/${it.slug}/")
            }
        }

        return MangasPage(mangas, false)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.parseAs<MainPage>()

        val mangas = document.top_10.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$baseUrl${it.capa}"
                setUrlWithoutDomain("/${it.slug}/")
            }
        }

        return MangasPage(mangas, false)
    }

    private fun loggedVerifyInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val pathSegments = response.request.url.pathSegments
        if (response.request.url.pathSegments.contains("login") || pathSegments.isEmpty()) {
            throw Exception("Faça o login na WebView para acessar o contéudo")
        }
        if (response.code == 429) {
            throw Exception("A LuraToon lhe bloqueou por acessar rápido demais, aguarde por volta de 1 minuto e tente novamente")
        }
        return response
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
}
