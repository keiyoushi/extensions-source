package eu.kanade.tachiyomi.extension.pt.rfdragonscan

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RFDragonScan :
    HttpSource(),
    ConfigurableSource {

    override val name = "RF Dragon Scan"

    override val baseUrl = "https://rfdragonscan.net"

    override val lang = "pt-BR"

    override val supportsLatest = false

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .addInterceptor(::loginInterceptor)
        .addInterceptor(::migrationInterceptor)
        .build()

    private val apiHeaders by lazy {
        headersBuilder().add("Rsc", "1").build()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/projetos?page=$page", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.body.string().extractNextJsRsc<ProjectsPageDto>()
            ?: return MangasPage(emptyList(), false)

        val mangas = dto.projects.map { it.toSManga() }

        return MangasPage(mangas, dto.pagination?.hasNextPage == true)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("term", query)
        }

        return GET(url.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String {
        if (!UUID_REGEX.matches(manga.url)) {
            return "$baseUrl/projetos?term=${manga.url.trim('/').split('/').last()}"
        }
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!UUID_REGEX.matches(manga.url)) {
            return GET("$baseUrl/migrate${manga.url}", apiHeaders)
        }
        val pathSegments = manga.url.trim('/').split('/').filter { it.isNotEmpty() }
        val mangaId = pathSegments[0]
        val mangaSlug = pathSegments[1]

        val payload = "[\"$mangaId\",\"$mangaSlug\"]"
        val requestBody = payload.toRequestBody("text/plain;charset=UTF-8".toMediaType())

        val stateTree = """["",{"children":[["projectId","$mangaId","d"],{"children":[["linkId","$mangaSlug","d"],{"children":["__PAGE__",{},null,null]},null,null]}]},null,null,true]"""

        return POST(
            baseUrl + manga.url,
            actionHeaders("60bd903bddc3d9d07f2b58fe32f0238afd74e492d6", baseUrl + manga.url, stateTree),
            requestBody,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.body.string().extractNextJsRsc<MangaDetailsDto> {
            it is JsonObject && "synopsis" in it && "title" in it
        } ?: throw IOException("Manga details not found")

        return dto.toSManga()
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request {
        if (!UUID_REGEX.matches(manga.url)) {
            return GET("$baseUrl/migrate-chapters${manga.url}", apiHeaders)
        }
        val pathSegments = manga.url.trim('/').split('/').filter { it.isNotEmpty() }
        val mangaId = pathSegments[0]
        val mangaSlug = pathSegments[1]

        val payload = "[\"$mangaId\",\"$mangaSlug\"]"
        val requestBody = payload.toRequestBody("text/plain;charset=UTF-8".toMediaType())

        val stateTree = """["",{"children":[["projectId","$mangaId","d"],{"children":[["linkId","$mangaSlug","d"],{"children":["__PAGE__",{},null,null]},null,null]}]},null,null,true]"""

        return POST(
            baseUrl + manga.url,
            actionHeaders("6075c7373783e0d2488372dc7fcb9ffe1470bc41d2", baseUrl + manga.url, stateTree),
            requestBody,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val seasonList = response.body.string().extractNextJsRsc<SeasonListDto> {
            it is JsonObject && "groups" in it
        } ?: throw IOException("Chapters not found")

        val pathSegments = response.request.url.pathSegments.filter { it.isNotEmpty() }
        val mangaId = pathSegments[pathSegments.size - 2]
        val mangaSlug = pathSegments.last()

        val chapters = mutableListOf<SChapter>()

        seasonList.groups?.forEach { group ->
            group.chapters?.forEach { ch ->
                if (ch.isUpcoming == true || ch.hasRestriction == true) {
                    return@forEach
                }
                chapters.add(ch.toSChapter(mangaId, mangaSlug, dateFormat))
            }
        }

        return chapters.sortedByDescending {
            it.name.substringAfter("Capítulo ").toFloatOrNull() ?: 0f
        }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val pathSegments = chapter.url.trim('/').split('/').filter { it.isNotEmpty() }
        val mangaId = pathSegments[0]
        val mangaSlug = pathSegments[1]
        val chapterTitle = pathSegments[3]

        val payload = "[\"$mangaId\",\"$chapterTitle\"]"
        val requestBody = payload.toRequestBody("text/plain;charset=UTF-8".toMediaType())

        val stateTree = """["",{"children":[["projectId","$mangaId","d"],{"children":[["linkId","$mangaSlug","d"],{"children":["capitulo",{"children":[["chapterId","$chapterTitle","d"],{"children":["__PAGE__",{},null,null]}]}]}]}]},null,null,true]"""

        return POST(
            baseUrl + chapter.url,
            actionHeaders("605aecabcce97cec193f09ebe5fe3a9ae46e432ea2", baseUrl + chapter.url, stateTree),
            requestBody,
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.body.string().extractNextJsRsc<PagesDto> {
            it is JsonObject && "pages" in it
        } ?: throw IOException("Pages not found")

        return dto.toPages()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = EMAIL_PREF
            title = "Email"
            summary = "Email utilizado para login no RF Dragon Scan"
            dialogTitle = "Email"
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "Senha"
            summary = "Senha utilizada para login no RF Dragon Scan"
            dialogTitle = "Senha"
        }.also(screen::addPreference)
    }

    private var actionIdCache: String? = null

    private fun getActionId(): String {
        actionIdCache?.let { return it }

        val html = network.client.newCall(GET("$baseUrl/login", headers)).execute().use { it.body.string() }

        ACTION_ID_HTML_REGEX.find(html)?.let {
            val id = it.groupValues[1]
            actionIdCache = id
            return id
        }

        val chunkUrls = CHUNK_URL_REGEX.findAll(html)
            .map { it.groupValues[1] }
            .toList()

        for (url in chunkUrls) {
            try {
                network.client.newCall(GET(baseUrl + url, headers)).execute().use { res ->
                    val js = res.body.string()
                    if (js.contains("\"login\"")) {
                        val idMatch = ACTION_ID_JS_REGEX.find(js)
                        if (idMatch != null) {
                            val id = idMatch.groupValues[1]
                            actionIdCache = id
                            return id
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore and continue searching
            }
        }

        return "600165150b15a3870c9e076c863daec8d24748e458"
    }

    private fun actionHeaders(actionId: String, referer: String, stateTree: String): Headers {
        val encodedStateTree = java.net.URLEncoder.encode(stateTree, "UTF-8")
        return headersBuilder()
            .add("next-action", actionId)
            .add("next-router-state-tree", encodedStateTree)
            .add("Accept", "text/x-component")
            .add("Origin", baseUrl)
            .add("Referer", referer)
            .build()
    }

    private fun loginInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        val isLoggedIn = cookies.any { it.name == "access_token" && it.value.isNotEmpty() }

        if (isLoggedIn) {
            val response = chain.proceed(request)
            if (response.code != 401 && response.code != 403) {
                return response
            }
            response.close()
        }

        if (request.url.pathSegments.lastOrNull() == "login") {
            return chain.proceed(request)
        }

        synchronized(this) {
            val currentCookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            if (currentCookies.any { it.name == "access_token" && it.value.isNotEmpty() }) {
                return chain.proceed(request)
            }

            val email = preferences.getString(EMAIL_PREF, "") ?: ""
            val password = preferences.getString(PASSWORD_PREF, "") ?: ""

            if (email.isBlank() || password.isBlank()) {
                throw IOException("Configure seu email e senha nas configurações da extensão para acessar capítulos restritos.")
            }

            val actionId = getActionId()
            val payload = "[\"$email\",\"$password\"]"
            val loginBody = payload.toRequestBody("text/plain;charset=UTF-8".toMediaType())

            val loginHeaders = headersBuilder()
                .add("next-action", actionId)
                .add(
                    "next-router-state-tree",
                    "%5B%22%22%2C%7B%22children%22%3A%5B%22login%22%2C%7B%22children%22%3A%5B%22__PAGE__%22%2C%7B%7D%2Cnull%2Cnull%5D%7D%2Cnull%2Cnull%2Ctrue%5D%7D%2Cnull%2Cnull%2Ctrue%5D",
                )
                .add("Accept", "text/x-component")
                .add("Origin", baseUrl)
                .add("Referer", "$baseUrl/login")
                .build()

            val loginReq = POST("$baseUrl/login", loginHeaders, loginBody)

            val success = network.client.newCall(loginReq).execute().use { loginRes ->
                if (!loginRes.isSuccessful) {
                    throw IOException("Falha no login. Verifique suas credenciais.")
                }
                client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).any { it.name == "access_token" && it.value.isNotEmpty() }
            }

            if (!success) {
                throw IOException("Falha no login. Token de acesso não recebido.")
            }

            return chain.proceed(request)
        }
    }

    private fun migrationInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val firstSegment = request.url.pathSegments.firstOrNull()

        if (firstSegment == "migrate" || firstSegment == "migrate-chapters") {
            val oldPath = request.url.encodedPath
                .removePrefix("/migrate-chapters")
                .removePrefix("/migrate")
            val slug = oldPath.trim('/').split('/').last { it.isNotEmpty() }

            val searchUrl = "$baseUrl/projetos?term=$slug".toHttpUrl()
            val searchReq = GET(searchUrl, apiHeaders)
            val searchRes = chain.proceed(searchReq)

            val newUrlPath = searchRes.use { res ->
                val dto = res.body.string().extractNextJsRsc<ProjectsPageDto>()
                val project = dto?.projects?.firstOrNull { it.link == slug || it.title.contains(slug, ignoreCase = true) }
                    ?: throw IOException("Manga not found during migration")

                "/${project.id}/${project.link}"
            }

            val pathSegments = newUrlPath.trim('/').split('/')
            val mangaId = pathSegments[0]
            val mangaSlug = pathSegments[1]

            val actionId = if (firstSegment == "migrate") {
                "60bd903bddc3d9d07f2b58fe32f0238afd74e492d6"
            } else {
                "6075c7373783e0d2488372dc7fcb9ffe1470bc41d2"
            }

            val payload = "[\"$mangaId\",\"$mangaSlug\"]"
            val requestBody = payload.toRequestBody("text/plain;charset=UTF-8".toMediaType())

            val stateTree = """["",{"children":[["projectId","$mangaId","d"],{"children":[["linkId","$mangaSlug","d"],{"children":["__PAGE__",{},null,null]},null,null]}]},null,null,true]"""

            val actionRequest = POST(
                "$baseUrl$newUrlPath",
                actionHeaders(actionId, "$baseUrl$newUrlPath", stateTree),
                requestBody,
            )

            return chain.proceed(actionRequest)
        }

        return chain.proceed(request)
    }

    companion object {
        private const val EMAIL_PREF = "pref_email"
        private const val PASSWORD_PREF = "pref_password"

        private val UUID_REGEX = Regex("^/[0-9a-fA-F\\-]{36}/.*")

        private val ACTION_ID_HTML_REGEX = Regex("""name="\x24ACTION_ID_([a-f0-9]{40})"""")
        private val CHUNK_URL_REGEX = Regex("""src="(/_next/static/chunks/[^"]+\.js)"""")
        private val ACTION_ID_JS_REGEX = Regex("""createServerReference\("([a-f0-9]{40})",.*?,"login"\)""")
    }
}
