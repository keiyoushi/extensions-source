package eu.kanade.tachiyomi.extension.all.izneo

import android.text.InputType.TYPE_CLASS_TEXT
import android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class Izneo(override val lang: String) : ConfigurableSource, HttpSource() {
    override val name = "izneo"

    override val baseUrl = "$ORIGIN/$lang/webtoon"

    override val supportsLatest = true

    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor).build()

    private val apiUrl = "$ORIGIN/$lang/api/catalog/detail/webtoon"

    private val json by lazy { Injekt.get<Json>() }

    private val preferences by getPreferencesLazy()

    private inline val username: String
        get() = preferences.getString("username", "")!!

    private inline val password: String
        get() = preferences.getString("password", "")!!

    private val apiHeaders by lazy {
        headers.newBuilder().apply {
            set("X-Requested-With", "XMLHttpRequest")
            if (username.isNotEmpty() && password.isNotEmpty()) {
                set("Authorization", "Basic " + "$username:$password".btoa())
            }
        }.build()
    }

    private var seriesCount = 0

    override fun headersBuilder() = super.headersBuilder()
        .set("Cookie", "lang=$lang;").set("Referer", baseUrl)

    override fun latestUpdatesRequest(page: Int) =
        GET("$apiUrl/new?offset=${page - 1}&order=1&abo=0", apiHeaders)

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/topSales?offset=${page - 1}&order=0&abo=0", apiHeaders)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$apiUrl/free?offset=${page - 1}&order=3&abo=0", apiHeaders)

    override fun pageListRequest(chapter: SChapter) =
        GET(ORIGIN + "/book/" + chapter.id, apiHeaders)

    override fun imageRequest(page: Page) =
        GET(ORIGIN + "/book/" + page.imageUrl!!, apiHeaders)

    override fun latestUpdatesParse(response: Response) =
        response.parse().run {
            val count = try {
                get("series_count")!!.jsonPrimitive.int
            } catch (_: IllegalArgumentException) {
                return@run MangasPage(emptyList(), false)
            }
            val series = get("series")!!.jsonObject.values.flatMap {
                json.decodeFromJsonElement<List<Series>>(it)
            }.also { seriesCount += it.size }
            if (count == seriesCount) seriesCount = 0
            series.map {
                SManga.create().apply {
                    url = it.url
                    title = it.name
                    genre = it.genres
                    author = it.authors.joinToString()
                    artist = it.authors.joinToString()
                    thumbnail_url = "$ORIGIN/$lang${it.cover}"
                    description = it.toString()
                }
            }.let { MangasPage(it, seriesCount != 0) }
        }

    override fun popularMangaParse(response: Response) =
        latestUpdatesParse(response)

    override fun searchMangaParse(response: Response) =
        latestUpdatesParse(response)

    override fun pageListParse(response: Response) =
        response.parse()["data"]!!.jsonObject.run {
            val id = get("id")!!.jsonPrimitive.content
            get("pages")!!.jsonArray.map {
                val page = json.decodeFromJsonElement<AlbumPage>(it)
                Page(page.albumPageNumber, "", id + page.toString())
            }
        }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        super.fetchSearchManga(page, query, filters).map { mp ->
            mp.copy(mp.mangas.filter { it.title.contains(query, true) })
        }!!

    override fun fetchMangaDetails(manga: SManga) =
        Observable.just(manga.apply { initialized = true })!!

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfterLast('-')
        val url = "$ORIGIN/$lang/api/web/serie/$id/chapters/old"
        val chapters = mutableListOf<SChapter>()
        var cutoff = 0
        var current = LIMIT
        while (current == LIMIT) {
            val albums = client.newCall(GET("$url/$cutoff/$LIMIT", apiHeaders))
                .execute().parse()["albums"]!!.jsonArray
            albums.forEach {
                val album = json.decodeFromJsonElement<Album>(it)
                val chapter = SChapter.create()
                chapter.url = manga.url + album.path
                chapter.name = album.toString()
                chapter.date_upload = album.timestamp
                chapter.chapter_number = album.number
                chapters.add(chapter)
            }
            cutoff += LIMIT
            current = albums.size
        }
        return Observable.just(chapters)
    }

    override fun getMangaUrl(manga: SManga) = ORIGIN + manga.url

    override fun getChapterUrl(chapter: SChapter) = ORIGIN + chapter.url

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "username"
            title = "Username"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "password"
            title = "Password"

            setOnBindEditTextListener {
                it.inputType = TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD
            }

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.let(screen::addPreference)
    }

    private inline val Album.timestamp: Long
        get() = dateFormat.parse(publicationDate)?.time ?: 0L

    private inline val SChapter.id: String
        get() = url.substringAfterLast('-').substringBefore('/')

    private fun String.btoa() = Base64.encode(toByteArray(), Base64.DEFAULT)

    private fun Response.parse() =
        json.parseToJsonElement(body.string()).apply {
            if (jsonObject["status"]?.jsonPrimitive?.content == "error") {
                when (jsonObject["code"]?.jsonPrimitive?.content) {
                    "4" -> throw Error("You are not authorized to view this")
                    else -> throw Error(jsonObject["data"]?.jsonPrimitive?.content)
                }
            }
        }.jsonObject

    override fun mangaDetailsRequest(manga: SManga) =
        throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga) =
        throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException()

    override fun chapterListParse(response: Response) =
        throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    companion object {
        private const val ORIGIN = "https://www.izneo.com"

        private const val LIMIT = 50

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        }
    }
}
