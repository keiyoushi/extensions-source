package eu.kanade.tachiyomi.extension.all.mango

import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.Levenshtein
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class Mango :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    override val supportsLatest = false

    private val preferences: SharedPreferences by getPreferencesLazy()

    // ============================== Popular ==============================
    // Our popular manga are just our library of manga
    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/library?depth=0").parseOrLoginError<LibraryDto>()
        val mangas = result.titles ?: return MangasPage(emptyList(), false)

        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    private fun TitleDto.toSManga() = SManga.create().apply {
        url = "/book/$id"
        title = displayName
        thumbnail_url = basePath + coverUrl
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================
    // Default is to just return the whole library for searching
    // Here the best we can do is just match manga based on their titles
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val queryLower = query.lowercase()
        val mangas = getPopularManga(1).mangas
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
        val results2 = mangas.map { Pair(textDistance2.distance(it.title.lowercase(), queryLower), it) }
            .filter { it.first < 0.3 }
            .sortedBy { it.first }
            .map { it.second }

        val combinedResults = results.union(results2).toList()

        return MangasPage(combinedResults, false)
    }

    // ============================== Details ==============================
    // The chapter url will contain how many pages the chapter contains for our page list endpoint
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = client.get(apiUrl + manga.url + "?sort=auto").parseOrLoginError<TitleDto>()
        return SMangaUpdate(result.toSManga(), listChapters(result))
    }

    // ============================= Chapters ==============================
    // Helper function for listing chapters and chapters in nested titles recursively
    private fun listChapters(titleObj: TitleDto): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val topChapters = titleObj.entries?.map {
            SChapter.create().apply {
                name = it.displayName
                url = "/page/${it.titleId}/${it.id}/${it.pages}/"
                date_upload = 1000L * it.mtime
            }
        }
        val subChapters = titleObj.titles?.flatMap {
            val name = it.displayName
            listChapters(it).map { chp ->
                chp.name = "$name / ${chp.name}"
                chp
            }
        }
        if (topChapters != null) chapters += topChapters
        if (subChapters != null) chapters += subChapters
        return chapters
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val splitUrl = chapter.url.split("/").toMutableList()
        val numPages = splitUrl.removeAt(splitUrl.size - 2).toInt()
        val baseUrlChapter = splitUrl.joinToString("/")
        return (1..numPages).map { i ->
            Page(
                index = i,
                imageUrl = "$apiUrl$baseUrlChapter$i",
            )
        }
    }

    // ============================= Utilities =============================
    private var apiCookies: String = ""

    private inline fun <reified T> Response.parseOrLoginError(): T = try {
        parseAs<T>()
    } catch (_: Exception) {
        apiCookies = ""
        throw Exception("Login Likely Failed. Try Refreshing.")
    }

    private val port: String get() = preferences.getString(PORT_TITLE, PORT_DEFAULT)!!
    private val username: String get() = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private val password: String get() = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!

    private fun migratePreferencesIfNeeded() {
        val oldUrl = preferences.getString(ADDRESS_TITLE, null)
        if (oldUrl != null) {
            preferences.edit()
                .putString("pref_custom_base_url_$id", oldUrl)
                .remove(ADDRESS_TITLE)
                .apply()
        }
    }

    private val basePath: String get() {
        migratePreferencesIfNeeded()
        return baseUrl.removeSuffix("/")
    }

    private val apiUrl: String get() = "$basePath/api"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("User-Agent", "Tachiyomi Mango v${AppInfo.getVersionName()}")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = dns(Dns.SYSTEM)
        .addInterceptor { authIntercept(it) }

    private fun authIntercept(chain: Interceptor.Chain): Response {
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
            .header("Cookie", apiCookies)
            .build()

        return chain.proceed(authRequest)
    }

    private fun doLogin(chain: Interceptor.Chain) {
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()

        val loginRequest = POST("$basePath/login", headers, formBody)
        val response = chain.proceed(loginRequest)

        if (!response.isSuccessful || response.header("Set-Cookie") == null) {
            response.close()
            throw IOException("Login Failed. Check Address and Credentials")
        }
        apiCookies = response.header("Set-Cookie")!!
        response.close()
    }

    // ============================== Settings =============================
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(PORT_TITLE, PORT_DEFAULT, "The port number to use if it's not the default 9000"))
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, "Your login username"))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, "Your login password", true))
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(
        title: String,
        default: String,
        summary: String,
        isPassword: Boolean = false,
    ): androidx.preference.EditTextPreference = androidx.preference.EditTextPreference(context).apply {
        key = title
        this.title = title
        val input = preferences.getString(title, null)
        this.summary = if (input.isNullOrEmpty()) summary else input
        this.setDefaultValue(default)
        dialogTitle = title

        if (isPassword) {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        setOnPreferenceChangeListener { _, newValue ->
            val strValue = newValue as String
            this.summary = strValue.ifEmpty { summary }
            apiCookies = ""
            Toast.makeText(context, "Settings applied.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val PORT_TITLE = "Server Port Number"
        private const val PORT_DEFAULT = "9000"
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = "admin"
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""
    }
}
