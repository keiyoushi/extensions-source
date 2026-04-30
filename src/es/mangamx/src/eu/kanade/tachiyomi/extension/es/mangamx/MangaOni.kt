package eu.kanade.tachiyomi.extension.es.mangamx

import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOni :
    HttpSource(),
    ConfigurableSource {

    override val name = "MangaOni"

    override val id: Long = 2202687009511923782

    override val baseUrl = "https://manga-oni.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun popularMangaRequest(page: Int) = GET(
        url = "$baseUrl/directorio?genero=false&estado=false&filtro=visitas&tipo=false&adulto=${if (hideNSFWContent()) "0" else "false"}&orden=desc&p=$page",
        headers = headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#article-div a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.select("img").attr("abs:data-src")
                title = element.select("div:eq(1)").text()
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/recientes?p=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div._1bJU3").map { element ->
            SManga.create().apply {
                thumbnail_url = element.select("img").attr("abs:data-src")
                element.selectFirst("a[data-test=latest-update-name]")?.also {
                    title = it.text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/${if (query.isNotBlank()) "buscar" else "directorio"}").buildUpon()

        if (query.isNotBlank()) {
            uri.appendQueryParameter("q", query)
        } else {
            val adultContent = filters.firstInstanceOrNull<AdultContentFilter>()?.toUriPart()
                ?: if (hideNSFWContent()) "0" else "false"
            uri.appendQueryParameter("adulto", adultContent)

            filters.firstInstanceOrNull<StatusFilter>()?.let { uri.appendQueryParameter("estado", it.toUriPart()) }
            filters.firstInstanceOrNull<TypeFilter>()?.let { uri.appendQueryParameter("tipo", it.toUriPart()) }
            filters.firstInstanceOrNull<GenreFilter>()?.let { uri.appendQueryParameter("genero", it.toUriPart()) }
            filters.firstInstanceOrNull<SortBy>()?.let {
                uri.appendQueryParameter("filtro", it.toUriPart())
                uri.appendQueryParameter("orden", if (it.state?.ascending == true) "asc" else "desc")
            }
        }
        uri.appendQueryParameter("p", page.toString())
        return GET(uri.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.isSuccessful) throw Exception("Búsqueda fallida ${response.code}")

        val document = response.asJsoup()

        val mangas = if (document.location().startsWith("$baseUrl/directorio")) {
            document.select("#article-div a").map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.attr("abs:href"))
                    thumbnail_url = element.select("img").attr("abs:data-src")
                    title = element.select("div:eq(1)").text()
                }
            }
        } else {
            document.select("#article-div > div").map { element ->
                SManga.create().apply {
                    thumbnail_url = element.select("img").attr("abs:src")
                    element.selectFirst("div a")?.also {
                        title = it.text()
                        setUrlWithoutDomain(it.attr("abs:href"))
                    }
                }
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            thumbnail_url = document.select("img[src*=cover]").attr("abs:src")
            description = document.select("div#sinopsis").lastOrNull()?.ownText()
            author = document.select("div#info-i").text().let {
                if (it.contains("Autor", true)) {
                    it.substringAfter("Autor:").substringBefore("Fecha:").trim()
                } else {
                    "N/A"
                }
            }
            artist = author
            genre = document.select("div#categ a").joinToString(", ") { it.text() }
            status = when (document.selectFirst("strong:contains(Estado) + span")?.text()) {
                "En desarrollo" -> SManga.ONGOING
                "Finalizado" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div#c_list a").map { element ->
            SChapter.create().apply {
                name = element.text()
                setUrlWithoutDomain(element.attr("abs:href"))
                chapter_number = element.select("span").attr("data-num").toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParse(element.select("span").attr("datetime"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val encoded = document.selectFirst("script:containsData(unicap)")
            ?.data()?.substringAfter("'")?.substringBefore("'")
            ?: throw Exception("unicap not found")
        val drop = encoded.length % 4
        val decoded = Base64.decode(encoded.dropLast(drop), Base64.DEFAULT).toString(Charset.defaultCharset())
        val path = decoded.substringBefore("||")

        return decoded.substringAfter("[").substringBefore("]").split(",").mapIndexed { i, file ->
            Page(i, imageUrl = path + file.removeSurrounding("\""))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filterList = mutableListOf(
            Filter.Header("NOTA: Se ignoran si se usa el buscador"),
            Filter.Separator(),
            SortBy(),
            StatusFilter(),
            TypeFilter(),
            GenreFilter(),
        )

        if (!hideNSFWContent()) {
            filterList.add(AdultContentFilter())
        }
        return FilterList(filterList)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val contentPref = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = CONTENT_PREF
            title = CONTENT_PREF_TITLE
            summary = CONTENT_PREF_SUMMARY
            setDefaultValue(CONTENT_PREF_DEFAULT_VALUE)
        }

        screen.addPreference(contentPref)
    }

    private fun hideNSFWContent(): Boolean = preferences.getBoolean(CONTENT_PREF, CONTENT_PREF_DEFAULT_VALUE)

    companion object {
        private const val CONTENT_PREF = "showNSFWContent"
        private const val CONTENT_PREF_TITLE = "Ocultar contenido +18"
        private const val CONTENT_PREF_SUMMARY = "Ocultar el contenido erótico en mangas populares y filtros, no funciona en los mangas recientes ni búsquedas textuales."
        private const val CONTENT_PREF_DEFAULT_VALUE = false
    }
}
