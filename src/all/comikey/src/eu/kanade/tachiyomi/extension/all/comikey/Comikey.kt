package eu.kanade.tachiyomi.extension.all.comikey

import android.annotation.SuppressLint
import android.webkit.WebResourceResponse
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.tryParse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class Comikey :
    KeiSource(),
    ConfigurableSource {

    private val defaultLanguage: String get() = if (baseUrl == "https://br.comikey.com") "pt-BR" else "en"

    private val gundamUrl: String = "https://gundam.comikey.net"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    private val preferences = getPreferences()

    override suspend fun getPopularManga(page: Int) = parsePopularManga(client.get("$baseUrl/comics/?order=-views&page=$page"))

    override suspend fun getLatestUpdates(page: Int) = parsePopularManga(client.get("$baseUrl/comics/?page=$page"))

    private fun parsePopularManga(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.series-listing[data-view=list] > ul > li").map(::searchMangaFromElement)
        val hasNextPage = document.selectFirst("ul.pagination li.next-page:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) error("Unsupported url")
        val segments = url.pathSegments
        if (segments.size < 3) return null
        val slug = "${segments[1]}/${segments[2]}"
        return parseMangaDetails(client.get("$baseUrl/comics/$slug").asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/comics/".toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            if (query.length >= 2) {
                addQueryParameter("q", query)
            }

            filters.ifEmpty { getFilterList() }
                .filterIsInstance<UriFilter>()
                .forEach { it.addToUri(this) }
        }.build()

        return parsePopularManga(client.get(url))
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("div.series-data span.title a")!!.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text()
        }

        description = buildString {
            append(element.select("div.excerpt p").text())
            append("\n\n")
            append(element.select("div.desc p").text())
        }
        genre = element.select("ul.category-listing li a").joinToString { it.text() }
        thumbnail_url = element.selectFirst("div.image picture img")?.attr("abs:src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = if (fetchChapters) parseChapterList(document) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document): SManga {
        val data = document.selectFirst("script#comic")!!.data().parseAs<ComikeyComic>()

        return SManga.create().apply {
            url = data.link
            title = data.name
            author = data.author.joinToString { it.name }
            artist = data.artist.joinToString { it.name }
            description = buildString {
                append("\"")
                append(data.excerpt)
                append("\"\n\n")
                append(data.description)
            }
            thumbnail_url = "$baseUrl${data.fullCover}"
            status = when (data.updateStatus) {
                // HACK: Comikey Brasil
                0 -> when {
                    data.updateText.startsWith("toda", true) -> SManga.ONGOING
                    listOf("em pausa", "hiato").any { data.updateText.startsWith(it, true) } -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }

                1 -> SManga.COMPLETED

                3 -> SManga.ON_HIATUS

                in 4..14 -> SManga.ONGOING

                // daily, weekly, bi-weekly, monthly, every day of the week
                else -> SManga.UNKNOWN
            }
            genre = buildList(data.tags.size + 1) {
                addAll(data.tags.map { it.name })

                when (data.format) {
                    0 -> add("Comic")
                    1 -> add("Manga")
                    2 -> add("Webtoon")
                    else -> {}
                }
            }.joinToString()
        }
    }

    private suspend fun parseChapterList(document: Document): List<SChapter> {
        val segments = document.location().toHttpUrl().pathSegments
        val mangaSlug = segments[1]
        val mangaData = document.selectFirst("script#comic")!!.data().parseAs<ComikeyComic>()
        val defaultChapterPrefix = if (mangaData.format == 2) "episode" else "chapter"

        val chapterUrl = gundamUrl.toHttpUrl().newBuilder().apply {
            val mangaId = segments[2]
            val gundamToken = document.selectFirst("script:containsData(GUNDAM.token)")
                ?.data()
                ?.substringAfter("= \"")
                ?.substringBefore("\";")

            if (gundamToken != null) {
                addPathSegment("comic")
            } else {
                addPathSegment("comic.public")
            }

            addPathSegment(mangaId)
            addPathSegment("episodes")
            addQueryParameter("language", lang.lowercase())
            gundamToken?.let { addQueryParameter("token", gundamToken) }
        }.build()

        val data = client.get(chapterUrl).parseAs<ComikeyEpisodeListResponse>()
        val currentTime = System.currentTimeMillis()

        return data.episodes
            .filter { it.readable || !hideLockedChapters }
            .map {
                SChapter.create().apply {
                    url = "/read/$mangaSlug/${makeEpisodeSlug(it, defaultChapterPrefix)}/"
                    name = buildString {
                        append(it.title)

                        if (it.subtitle != null) {
                            append(": ")
                            append(it.subtitle)
                        }
                    }
                    chapter_number = it.number
                    date_upload = Instant.tryParse(it.releasedAt)
                }
            }
            .filter { it.date_upload <= currentTime }
            .reversed()
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val doc = client.get(getMangaUrl(manga)).asJsoup()

        return doc.select("div.similar-series li").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(a.attr("abs:href"))
                title = a.attr("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
    }

    private val mutexLock = Mutex()

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val interfaceName = randomString()
        var manifestRedirect: HttpUrl? = null

        val payload = mutexLock.withLock {
            try {
                runWebView<Payload>(timeout = 30.seconds) {
                    userAgent = headers["User-Agent"]!!
                    blockImages = true
                    javaScriptEnabled = true
                    domStorageEnabled = true

                    jsBridge(interfaceName) { message ->
                        resolve(message.parseAs<Payload>())
                    }

                    onPageStarted {
                        evaluateJs(webviewScript(interfaceName, intl))
                    }

                    // If you're logged in, the manifest URL sent to the client is not a direct link;
                    // it only redirects to the real one when you call it.
                    // In order to avoid requesting again later, we intercept it here.
                    interceptRequest { request ->
                        val url = request.url
                        if (url.host?.matches(RELAY_HOST_REGEX) == true || url.path?.endsWith("/manifest") == true) {
                            val requestHeaders = headers.newBuilder().apply {
                                request.requestHeaders.entries.forEach {
                                    set(it.key, it.value)
                                }
                                removeAll("X-Requested-With")
                            }.build()

                            val response = client.newCall(GET(url.toString(), requestHeaders)).execute()
                            manifestRedirect = response.request.url

                            WebResourceResponse(
                                response.headers["Content-Type"] ?: "application/divina+json+vnd.e4p.drm",
                                null,
                                response.code,
                                "OK",
                                response.headers.toMap(),
                                response.body.byteStream(),
                            )
                        }
                        null
                    }

                    loadUrl(
                        "$baseUrl${chapter.url}",
                        buildMap {
                            putAll(headers.toMap())
                            put("X-Requested-With", randomString())
                        },
                    )
                }
            } catch (_: WebViewTimeoutException) {
                throw Exception(intl["error_timed_out_decrypting_image_links"])
            }
        }

        payload.error?.let { error(it) }

        val manifestUrl = manifestRedirect ?: payload.manifestUrl!!.toHttpUrl()

        val manifest = payload.manifest!!

        val isWebtoon = manifest.metadata.readingProgression == "ttb"

        return manifest.readingOrder.mapIndexed { i, it ->
            val url = manifestUrl.newBuilder().apply {
                removePathSegment(manifestUrl.pathSize - 1)

                if (it.alternate.isNotEmpty()) {
                    addPathSegments(
                        if (it.height == 2048 && it.type == "image/jpeg") {
                            it.alternate.first { alt ->
                                val dimension = if (isWebtoon) alt.width else alt.height

                                dimension <= 1536 && alt.type == "image/webp"
                            }
                        } else {
                            it.alternate.first { alt ->
                                alt.type == "image/webp"
                            }
                        }.href,
                    )
                } else {
                    addPathSegments(it.href)
                }

                addQueryParameter("act", payload.act)
            }.toString()

            Page(i, imageUrl = url)
        }
    }

    override fun getFilterList(data: JsonElement?) = getComikeyFilters(intl)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_CHAPTERS
            title = intl["pref_hide_locked_chapters"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(PREF_HIDE_LOCKED_CHAPTERS, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)
    }

    private val hideLockedChapters: Boolean get() =
        preferences.getBoolean(PREF_HIDE_LOCKED_CHAPTERS, false)

    private fun randomString(): String {
        val length = (10..20).random()

        return buildString(length) {
            val charPool = ('a'..'z') + ('A'..'Z')

            for (i in 0 until length) {
                append(charPool.random())
            }
        }
    }

    private fun makeEpisodeSlug(episode: ComikeyEpisode, defaultChapterPrefix: String): String {
        val e4pid = episode.id.split("-", limit = 2).last()
        val chapterPrefix = if (defaultChapterPrefix == "chapter" && lang != defaultLanguage) {
            when (lang) {
                "es" -> "capitulo-espanol"
                "pt-br" -> "capitulo-portugues"
                "fr" -> "chapitre-francais"
                "id" -> "bab-bahasa"
                else -> "chapter"
            }
        } else {
            defaultChapterPrefix
        }

        return "$e4pid/$chapterPrefix-${episode.number.toString().removeSuffix(".0").replace(".", "-")}"
    }

    private fun webviewScript(interfaceName: String, intl: Intl) = """
        document.addEventListener("DOMContentLoaded", (e) => {
            if (document.querySelector("#unlock-full")) {
                $interfaceName.post(JSON.stringify({error: "${intl["error_locked_chapter_unlock_in_webview"]}"}));
            }
        });

        document.addEventListener(
            "you-right-now:reeeeeee",
            async (e) => {
                const postError = (error) => $interfaceName.post(JSON.stringify({ error }));
                try {
                    const db = await new Promise((resolve, reject) => {
                        const request = indexedDB.open("firebase-app-check-database");

                        request.onsuccess = (event) => resolve(event.target.result);
                        request.onerror = (event) => reject(event.target);
                    });

                    const act = await new Promise((resolve, reject) => {
                        db.onerror = (event) => reject(event.target);

                        const request = db.transaction("firebase-app-check-store").objectStore("firebase-app-check-store").getAll();

                        request.onsuccess = (event) => {
                            const entries = event.target.result;
                            db.close();

                            if (entries.length < 1) {
                                postError('${intl["error_open_in_webview_then_try_again"]} (${intl["error_token_not_found"]}).');
                            }
                            const value = entries[0].value;
                            if (value.expireTimeMillis < Date.now()) {
                                postError('${intl["error_open_in_webview_then_try_again"]} (${intl["error_token_expired"]}).');
                            }
                            resolve(value.token)
                        }
                    });

                    const manifest = JSON.parse(document.querySelector("#lmao-init").textContent).manifest;
                    $interfaceName.post(JSON.stringify({
                        manifestUrl: manifest,
                        act,
                        manifest: JSON.parse(await e.detail)
                    }));
                } catch (e) {
                    postError('${intl["error_unknown_error"]}: ' + e);
                }
            },
            { once: true },
        );
    """.trimIndent()

    companion object {
        internal const val PREF_HIDE_LOCKED_CHAPTERS = "hide_locked_chapters"
        private val RELAY_HOST_REGEX = Regex("""relay-\w+\.(epub\.rocks|comikey\.com)""")
    }
}
