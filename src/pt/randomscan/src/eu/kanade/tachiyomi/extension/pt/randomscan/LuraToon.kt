package eu.kanade.tachiyomi.extension.pt.randomscan

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
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
import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/obra/${manga.url.trimStart('/')}", headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val data = response.parseAs<Manga>()
        title = data.titulo
        author = data.autor
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

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/main/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.parseAs<MainPage>()

        val mangas = document.lancamentos.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$baseUrl${it.capa}"
                setUrlWithoutDomain("/${it.slug}/")
            }
        }

        return MangasPage(mangas, false)
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
            val capSlug = capitulo.slug.trimStart('/')
            val mangaUrl = manga.url.trimEnd('/').trimStart('/')
            setUrlWithoutDomain("/api/obra/$mangaUrl/$capSlug")
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
            Page(i, baseUrl, "$baseUrl/api/cap-download/${capitulo.obra.id}/${capitulo.id}/$i?obra_id=${capitulo.obra.id}&cap_id=${capitulo.id}&slug=${pathSegments[2]}&cap_slug=${pathSegments[3]}")
        }
        return files
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/autocomplete/")
            addPathSegments(query)
        }.build()

        return GET(url, headers)
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

    private fun decryptFile(encryptedData: ByteArray, keyData: ByteArray): ByteArray {
        val keyHash = MessageDigest.getInstance("SHA-256").digest(keyData)

        val key: SecretKey = SecretKeySpec(keyHash, "AES")

        val counter = encryptedData.copyOfRange(0, 8)
        val iv = IvParameterSpec(counter)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)

        val decryptedData = cipher.doFinal(encryptedData.copyOfRange(8, encryptedData.size))

        return decryptedData
    }

    override fun zipGetByteStream(request: Request, response: Response): InputStream {
        val keyData = listOf("obra_id", "slug", "cap_id", "cap_slug").joinToString("") {
            request.url.queryParameterValues(it).first().toString()
        }.toByteArray(StandardCharsets.UTF_8)
        val encryptedData = response.body.bytes()
        val decryptedData = decryptFile(encryptedData, keyData)
        return ByteArrayInputStream(decryptedData)
    }
}
