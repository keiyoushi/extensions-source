package eu.kanade.tachiyomi.extension.en.spyfakku

import android.annotation.SuppressLint
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

class SpyFakku :
    HttpSource(),
    ConfigurableSource {

    override val name = "SpyFakku"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences = getPreferences()

    override val baseUrl: String
        get() {
            val index = preferences.getString(MIRROR_PREF_KEY, "0")!!.toInt()
                .coerceAtMost(mirrors.size - 1)

            return mirrors[index]
        }

    private val baseImageUrl = "$TMP_CDN_URL/image"

    private val baseApiUrl get() = "$baseUrl/api"

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        // add referer in interceptor due to domain change preference
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Referer", "$baseUrl/")
                .header("Origin", baseUrl)
                .build()

            chain.proceed(request)
        }
        // change domain of image urls
        .addInterceptor { chain ->
            val url = chain.request().url
            if (url.host == TMP_CDN_DOMAIN) {
                val (host, port) = baseUrl.toHttpUrl().let { it.host to it.port }
                val newUrl = url.newBuilder()
                    .scheme("https")
                    .host(host)
                    .port(port)
                    .build()

                val request = chain.request().newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(request)
            } else {
                chain.proceed(chain.request())
            }
        }
        // airdns domain is self-signed
        .apply {
            val naiveTrustManager =
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate?> = emptyArray()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                }

            val insecureSocketFactory = SSLContext.getInstance("SSL").apply {
                val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
                init(null, trustAllCerts, SecureRandom())
            }.socketFactory

            sslSocketFactory(insecureSocketFactory, naiveTrustManager)
            hostnameVerifier { _, _ -> true }
        }
        // airdns domain is protected by Anibus
        .addInterceptor(AnibusInterceptor)
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    private val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override fun popularMangaRequest(page: Int): Request = GET("$baseApiUrl/library?sort=released_at&page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseApiUrl/library?sort=created_at&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val library = response.parseAs<HentaiLib>()

        val mangas = library.archives.map { it.toSManga() }

        val hasNextPage = library.page * library.limit < library.total

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseApiUrl/library".toHttpUrl().newBuilder().apply {
            val terms = mutableListOf(query.trim())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.getValue())
                        if (filter.getValue() == "random") addQueryParameter("seed", generateSeed())
                        addQueryParameter("order", if (filter.state!!.ascending) "asc" else "desc")
                    }

                    is SelectFilter -> {
                        addQueryParameter("limit", filter.vals[filter.state])
                    }

                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            terms += filter.state.split(",").filter { it.isNotBlank() }.map { tag ->
                                val trimmed = tag.trim().replace(" ", "_")
                                (if (trimmed.startsWith("-")) "-" else "") + filter.type + ":" + trimmed.removePrefix("-")
                            }
                        }
                    }

                    else -> {}
                }
            }
            addQueryParameter("q", terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun getFilterList() = getFilters()

    // Details
    private val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
    private val releasedAtFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val createdAtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun getAdditionals(data: List<JsonElement>): ShortHentai {
        fun Collection<JsonElement>.getTags(): List<Name> = this.map {
            Name(data[it.jsonPrimitive.int + 2].jsonPrimitive.content, data[it.jsonPrimitive.int + 3].jsonPrimitive.content)
        }
        val hentaiIndexes = json.decodeFromJsonElement<HentaiIndexes>(data[1])

        val hash = data[hentaiIndexes.hash].jsonPrimitive.content
        val thumbnail = data[hentaiIndexes.thumbnail].jsonPrimitive.int

        val description = data[hentaiIndexes.description].jsonPrimitive.contentOrNull
        val releasedAt = data[hentaiIndexes.released_at].jsonPrimitive.content
        val createdAt = data[hentaiIndexes.created_at].jsonPrimitive.content
        val size = data[hentaiIndexes.size].jsonPrimitive.long
        val pages = data[hentaiIndexes.pages].jsonPrimitive.int

        val tags = data[hentaiIndexes.tags].jsonArray.emptyToNull()?.getTags()
        return ShortHentai(
            hash = hash,
            thumbnail = thumbnail,
            description = description,
            released_at = releasedAt,
            created_at = createdAt,
            tags = tags,
            size = size,
            pages = pages,
        )
    }
    private fun <T> Collection<T>.emptyToNull(): Collection<T>? = this.ifEmpty { null }

    private fun Hentai.toSManga() = SManga.create().apply {
        title = this@toSManga.title
        url = "/g/$id?$pages&hash=$hash"
        author = tags?.filter { it.namespace == "circle" }?.joinToString { it.name }
        artist = tags?.filter { it.namespace == "artist" }?.joinToString { it.name }
        genre = tags?.filter { it.namespace == "tag" }?.joinToString { it.name }
        thumbnail_url = "$baseImageUrl/$hash/$thumbnail?type=cover"
        status = SManga.COMPLETED
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val response1: Response = client.newCall(mangaDetailsRequest(manga)).execute()
        val add: ShortHentai

        if (response1.isSuccessful) {
            add = response1.parseAs<ShortHentai>()
        } else {
            var response: Response = client.newCall(mangaDetailsRequest(manga)).execute()
            var attempts = 0
            while (attempts < 3 && response.code != 200) {
                try {
                    response = client.newCall(mangaDetailsRequest(manga)).execute()
                } catch (_: Exception) {
                } finally {
                    attempts++
                }
            }
            add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
        }

        return Observable.just(
            manga.apply {
                with(add) {
                    val tags = tags?.groupBy { it.namespace }

                    url = "/g/$id?$pages&hash=$hash"
                    author = (tags?.get("circle") ?: tags?.get("artist"))?.joinToString { it.name }
                    artist = tags?.get("artist")?.joinToString { it.name }
                    thumbnail_url = "$baseImageUrl/$hash/$thumbnail?type=cover"
                    genre = tags?.get("tag")?.joinToString { it.name }
                    this@apply.description = buildString {
                        description?.let {
                            append(it, "\n\n")
                        }

                        tags?.get("circle")?.emptyToNull()?.joinToString { it.name }?.let {
                            append("Circles: ", it, "\n")
                        }
                        tags?.get("publisher")?.emptyToNull()?.joinToString { it.name }?.let {
                            append("Publishers: ", it, "\n")
                        }
                        tags?.get("magazine")?.emptyToNull()?.joinToString { it.name }?.let {
                            append("Magazines: ", it, "\n")
                        }
                        tags?.get("event")?.emptyToNull()?.joinToString { it.name }?.let {
                            append("Events: ", it, "\n\n")
                        }
                        tags?.get("parody")?.emptyToNull()?.joinToString { it.name }?.let {
                            append("Parodies: ", it, "\n")
                        }
                        append("Pages: ", pages, "\n\n")

                        try {
                            releasedAt?.let {
                                releasedAtFormat.parse(it)?.let {
                                    append("Released: ", dateReformat.format(it.time), "\n")
                                }
                            }
                        } catch (_: Exception) {
                        }

                        try {
                            createdAt?.let {
                                createdAtFormat.parse(it)?.let {
                                    append("Added: ", dateReformat.format(it.time), "\n")
                                }
                            }
                        } catch (_: Exception) {
                        }

                        append(
                            "Size: ",
                            when {
                                size >= 300 * 1000 * 1000 -> "${"%.2f".format(size / (1000.0 * 1000.0 * 1000.0))} GB"
                                size >= 100 * 1000 -> "${"%.2f".format(size / (1000.0 * 1000.0))} MB"
                                size >= 1000 -> "${"%.2f".format(size / (1000.0))} kB"
                                else -> "$size B"
                            },
                        )
                    }
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                    initialized = true
                }
            },
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        manga.url = Regex("^/archive/(\\d+)/.*").replace(manga.url) { "/g/${it.groupValues[1]}" }
        return GET(baseApiUrl + manga.url.substringBefore("?"), headers)
    }
    private fun mangaDetailsRequest2(manga: SManga): Request {
        manga.url = Regex("^/archive/(\\d+)/.*").replace(manga.url) { "/g/${it.groupValues[1]}" }
        return GET(baseUrl + manga.url.substringBefore("?") + "/__data.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.substringBefore("?")

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val response1: Response = client.newCall(chapterListRequest(manga)).execute()
        val add: ShortHentai

        if (response1.isSuccessful) {
            add = response1.parseAs<ShortHentai>()
        } else {
            var response: Response = client.newCall(chapterListRequest2(manga)).execute()
            var attempts = 0
            while (attempts < 3 && response.code != 200) {
                try {
                    response = client.newCall(mangaDetailsRequest(manga)).execute()
                } catch (_: Exception) {
                } finally {
                    attempts++
                }
            }
            add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
        }
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                    date_upload = releasedAtFormat.tryParse(add.released_at)
                },
            ),
        )
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore("?")

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    private fun chapterListRequest2(manga: SManga) = mangaDetailsRequest2(manga)

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (!chapter.url.contains("&hash=") && !chapter.url.contains("?")) {
            val response1 = client.newCall(pageListRequest(chapter)).execute()
            if (response1.isSuccessful) {
                val hentai = response1.parseAs<Hentai>()
                return Observable.just(
                    List(hentai.pages) { index ->
                        Page(index, imageUrl = "$baseImageUrl/${hentai.hash}/${index + 1}")
                    },
                )
            }
            val response = client.newCall(pageListRequest2(chapter)).execute()
            val add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
            return Observable.just(
                List(add.pages) { index ->
                    Page(index, imageUrl = "$baseImageUrl/${add.hash}/${index + 1}")
                },
            )
        }
        val hash: String = chapter.url.substringAfter("hash=")
        val pages: Int = chapter.url.substringAfter("?").substringBefore("&").toInt()

        return Observable.just(
            List(pages) { index ->
                Page(index, imageUrl = "$baseImageUrl/$hash/${index + 1}")
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        chapter.url = Regex("^/archive/(\\d+)/.*").replace(chapter.url) { "/g/${it.groupValues[1]}" }
        return GET(baseApiUrl + chapter.url.substringBefore("?"), headers)
    }
    private fun pageListRequest2(chapter: SChapter): Request {
        chapter.url = Regex("^/archive/(\\d+)/.*").replace(chapter.url) { "/g/${it.groupValues[1]}" }
        return GET(baseUrl + chapter.url.substringBefore("?") + "/__data.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    // Others
    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

    private fun generateSeed(): String {
        val length = Random.nextInt(4, 9)
        val string = StringBuilder(length)

        for (i in 0 until length) {
            string.append(charset.random())
        }

        return string.toString()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Preferred Mirror"
            entries = mirrors
            entryValues = Array(mirrors.size) { it.toString() }
            summary = "%s"
            setDefaultValue("0")
        }.also(screen::addPreference)
    }
}

private const val MIRROR_PREF_KEY = "pref_mirror"
private val mirrors = arrayOf(
    "https://hentalk.pw",
    "https://fakku.cc",
    "https://fakkuonion.airdns.org:4096",
)
private const val TMP_CDN_DOMAIN = "127.0.0.1"
private const val TMP_CDN_URL = "http://$TMP_CDN_DOMAIN"
