package eu.kanade.tachiyomi.extension.all.mango

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.Levenshtein
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class Mango : ConfigurableSource, UnmeteredSource, HttpSource() {

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/library?depth=0", headersBuilder().build())

    // Our popular manga are just our library of manga
    override fun popularMangaParse(response: Response): MangasPage {
        val result = try {
            json.decodeFromString<JsonObject>(response.body.string())
        } catch (e: Exception) {
            apiCookies = ""
            throw Exception("Login Likely Failed. Try Refreshing.")
        }
        val mangas = result["titles"]!!.jsonArray
        return MangasPage(
            mangas.jsonArray.map {
                SManga.create().apply {
                    url = "/book/" + it.jsonObject["id"]!!.jsonPrimitive.content
                    title = it.jsonObject["display_name"]!!.jsonPrimitive.content
                    thumbnail_url = baseUrl + it.jsonObject["cover_url"]!!.jsonPrimitive.content
                }
            },
            false,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    // Default is to just return the whole library for searching
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    // Here the best we can do is just match manga based on their titles
    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val queryLower = query.lowercase()
        val mangas = popularMangaParse(response).mangas
        val exactMatch = mangas.firstOrNull { it.title.lowercase() == queryLower }
        if (exactMatch != null) {
            return MangasPage(listOf(exactMatch), false)
        }

        // Text distance algorithms
        val textDistance = Levenshtein()
        val textDistance2 = JaroWinkler()

        // Take results that potentially start the same
        val results = mangas.filter {
            val title = it.title.lowercase()
            val query2 = queryLower.take(7)
            (title.startsWith(query2, true) || title.contains(query2, true))
        }.sortedBy { textDistance.distance(queryLower, it.title.lowercase()) }

        // Take similar results
        val results2 = mangas.map { Pair(textDistance2.distance(it.title.lowercase(), query), it) }
            .filter { it.first < 0.3 }.sortedBy { it.first }.map { it.second }
        val combinedResults = results.union(results2)

        // Finally return the list
        return MangasPage(combinedResults.toList(), false)
    }

    // Stub
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + "/api" + manga.url, headers)

    // This will just return the same thing as the main library endpoint
    override fun mangaDetailsParse(response: Response): SManga {
        val result = try {
            json.decodeFromString<JsonObject>(response.body.string())
        } catch (e: Exception) {
            apiCookies = ""
            throw Exception("Login Likely Failed. Try Refreshing.")
        }
        return SManga.create().apply {
            url = "/book/" + result.jsonObject["id"]!!.jsonPrimitive.content
            title = result.jsonObject["display_name"]!!.jsonPrimitive.content
            thumbnail_url = baseUrl + result.jsonObject["cover_url"]!!.jsonPrimitive.content
        }
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + "/api" + manga.url + "?sort=auto", headers)

    // The chapter url will contain how many pages the chapter contains for our page list endpoint
    override fun chapterListParse(response: Response): List<SChapter> {
        val result = try {
            json.decodeFromString<JsonObject>(response.body.string())
        } catch (e: Exception) {
            apiCookies = ""
            throw Exception("Login Likely Failed. Try Refreshing.")
        }
        return listChapters(result)
    }

    // Helper function for listing chapters and chapters in nested titles recursively
    private fun listChapters(titleObj: JsonObject): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val topChapters = titleObj["entries"]?.jsonArray?.map { obj ->
            SChapter.create().apply {
                name = obj.jsonObject["display_name"]!!.jsonPrimitive.content
                url =
                    "/page/${obj.jsonObject["title_id"]!!.jsonPrimitive.content}/${obj.jsonObject["id"]!!.jsonPrimitive.content}/${obj.jsonObject["pages"]!!.jsonPrimitive.content}/"
                date_upload = 1000L * obj.jsonObject["mtime"]!!.jsonPrimitive.long
            }
        }
        val subChapters = titleObj["titles"]?.jsonArray?.map { obj ->
            val name = obj.jsonObject["display_name"]!!.jsonPrimitive.content
            listChapters(obj.jsonObject).map { chp ->
                chp.name = "$name / ${chp.name}"
                chp
            }
        }?.flatten()
        if (topChapters !== null) chapters += topChapters
        if (subChapters !== null) chapters += subChapters
        return chapters
    }

    // Stub
    override fun pageListRequest(chapter: SChapter): Request =
        throw UnsupportedOperationException("Not used")

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val splitUrl = chapter.url.split("/").toMutableList()
        val numPages = splitUrl.removeAt(splitUrl.size - 2).toInt()
        val baseUrlChapter = splitUrl.joinToString("/")
        val pages = mutableListOf<Page>()
        for (i in 1..numPages) {
            pages.add(
                Page(
                    index = i,
                    imageUrl = "$baseUrl/api$baseUrlChapter$i",
                ),
            )
        }
        return Observable.just(pages)
    }

    // Stub
    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String = ""
    override fun getFilterList(): FilterList = FilterList()

    override val name = "Mango"
    override val lang = "en"
    override val supportsLatest = false

    private val json: Json by injectLazy()
    override val baseUrl by lazy { getPrefBaseUrl() }
    private val port by lazy { getPrefPort() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }
    private var apiCookies: String = ""

    override fun headersBuilder(): Headers.Builder =
        Headers.Builder()
            .add("User-Agent", "Tachiyomi Mango v${AppInfo.getVersionName()}")

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .dns(Dns.SYSTEM)
            .addInterceptor { authIntercept(it) }
            .build()

    private fun authIntercept(chain: Interceptor.Chain): Response {
        // Check that we have our username and password to login with
        val request = chain.request()
        if (username.isEmpty() || password.isEmpty()) {
            throw IOException("Missing username or password")
        }

        // Do the login if we have not gotten the cookies yet
        if (apiCookies.isEmpty() || !apiCookies.contains("mango-sessid-$port", true)) {
            doLogin(chain)
        }

        // Append the new cookie from the api
        val authRequest = request.newBuilder()
            .addHeader("Cookie", apiCookies)
            .build()

        return chain.proceed(authRequest)
    }

    private fun doLogin(chain: Interceptor.Chain) {
        // Try to login
        val formHeaders: Headers = headersBuilder()
            .add("ContentType", "application/x-www-form-urlencoded")
            .build()
        val formBody: RequestBody = FormBody.Builder()
            .addEncoded("username", username)
            .addEncoded("password", password)
            .build()
        val loginRequest = POST("$baseUrl/login", formHeaders, formBody)
        val response = chain.proceed(loginRequest)
        if (response.code != 200 || response.header("Set-Cookie") == null) {
            throw IOException("Login Failed. Check Address and Credentials")
        }
        // Save the cookies from the response
        apiCookies = response.header("Set-Cookie")!!
        response.close()
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, "The URL to access your Mango instance. Please include the port number if you didn't set up a reverse proxy"))
        screen.addPreference(screen.editTextPreference(PORT_TITLE, PORT_DEFAULT, "The port number to use if it's not the default 9000"))
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, "Your login username"))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, "Your login password", true))
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(title: String, default: String, summary: String, isPassword: Boolean = false): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            val input = preferences.getString(title, null)
            this.summary = if (input == null || input.isEmpty()) summary else input
            this.setDefaultValue(default)
            dialogTitle = title

            if (isPassword) {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    apiCookies = ""
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    // We strip the last slash since we will append it above
    private fun getPrefBaseUrl(): String {
        var path = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
        if (path.isNotEmpty() && path.last() == '/') {
            path = path.substring(0, path.length - 1)
        }
        return path
    }
    private fun getPrefPort(): String = preferences.getString(PORT_TITLE, PORT_DEFAULT)!!
    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = ""
        private const val PORT_TITLE = "Server Port Number"
        private const val PORT_DEFAULT = "9000"
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = "admin"
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""
    }
}
