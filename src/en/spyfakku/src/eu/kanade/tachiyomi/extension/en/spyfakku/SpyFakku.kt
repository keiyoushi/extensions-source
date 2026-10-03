package eu.kanade.tachiyomi.extension.en.spyfakku

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.array
import keiyoushi.utils.int
import keiyoushi.utils.long
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SpyFakku : KeiSource() {

    private val baseImageUrl = "$TMP_CDN_URL/image"

    private val baseApiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
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

        addInterceptor(AnibusInterceptor)
        rateLimit(2, 1.seconds)
    }

    private val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseApiUrl/library?sort=released_at&page=$page").toMangasPage()

    private fun Response.toMangasPage(): MangasPage {
        val library = parseAs<HentaiLib>()
        val mangas = library.archives.map { it.toSManga() }
        val hasNextPage = library.page * library.limit < library.total

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseApiUrl/library?sort=created_at&page=$page").toMangasPage()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return client.get(url).toMangasPage()
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val add = getShortHentai(manga)

        val details = SManga.create().apply {
            url = manga.url
            title = manga.title
            status = SManga.COMPLETED
            with(add) {
                val groupedTags = tags?.groupBy { it.namespace }

                author = (groupedTags?.get("circle") ?: groupedTags?.get("artist"))?.joinToString { it.name }
                artist = groupedTags?.get("artist")?.joinToString { it.name }
                thumbnail_url = "$baseImageUrl/$hash/$thumbnail?type=cover"
                genre = groupedTags?.get("tag")?.joinToString { it.name }

                this@apply.description = buildString {
                    description?.let { append(it, "\n\n") }

                    groupedTags?.get("circle")?.ifEmpty { null }?.joinToString { it.name }?.let { append("Circles: ", it, "\n") }
                    groupedTags?.get("publisher")?.ifEmpty { null }?.joinToString { it.name }?.let { append("Publishers: ", it, "\n") }
                    groupedTags?.get("magazine")?.ifEmpty { null }?.joinToString { it.name }?.let { append("Magazines: ", it, "\n") }
                    groupedTags?.get("event")?.ifEmpty { null }?.joinToString { it.name }?.let { append("Events: ", it, "\n\n") }
                    groupedTags?.get("parody")?.ifEmpty { null }?.joinToString { it.name }?.let { append("Parodies: ", it, "\n") }

                    append("Pages: ", pages, "\n\n")

                    releasedAtFormat.tryParseDateTime(releasedAt?.take(19)).takeIf { it != 0L }?.let { date ->
                        append("Released: ", dateReformat.format(Instant.ofEpochMilli(date)), "\n")
                    }

                    createdAtFormat.tryParseDateTime(createdAt).takeIf { it != 0L }?.let { date ->
                        append("Added: ", dateReformat.format(Instant.ofEpochMilli(date)), "\n")
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
            }
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapterList = listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = manga.url
                date_upload = releasedAtFormat.tryParseDateTime(add.releasedAt?.take(19))
            },
        )

        return SMangaUpdate(details, chapterList)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.substringBefore("?")

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore("?")

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!chapter.url.contains("&hash=") && !chapter.url.contains("?")) {
            val path = ARCHIVE_REGEX.replace(chapter.url) { "/g/${it.groupValues[1]}" }.substringBefore("?")

            client.get(baseApiUrl + path, ensureSuccess = false).use { response ->
                if (response.isSuccessful) {
                    val hentai = response.parseAs<Hentai>()
                    return List(hentai.pages) { index ->
                        Page(index, imageUrl = "$baseImageUrl/${hentai.hash}/${index + 1}")
                    }
                }
            }

            repeat(3) {
                try {
                    client.get("$baseUrl$path/__data.json", ensureSuccess = false).use { response ->
                        if (response.isSuccessful) {
                            val add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
                            return List(add.pages) { index ->
                                Page(index, imageUrl = "$baseImageUrl/${add.hash}/${index + 1}")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            throw Exception("Failed to fetch page list")
        }

        val hash = chapter.url.substringAfter("hash=")
        val pages = chapter.url.substringAfter("?").substringBefore("&").toInt()

        return List(pages) { index ->
            Page(index, imageUrl = "$baseImageUrl/$hash/${index + 1}")
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = getFilters()

    // ============================= Utilities =============================

    private suspend fun getShortHentai(manga: SManga): ShortHentai {
        val path = ARCHIVE_REGEX.replace(manga.url) { "/g/${it.groupValues[1]}" }.substringBefore("?")

        client.get(baseApiUrl + path, ensureSuccess = false).use { response ->
            if (response.isSuccessful) {
                return response.parseAs<ShortHentai>()
            }
        }

        repeat(3) {
            try {
                client.get("$baseUrl$path/__data.json", ensureSuccess = false).use { response ->
                    if (response.isSuccessful) {
                        return getAdditionals(response.parseAs<Nodes>().nodes.last().data)
                    }
                }
            } catch (_: Exception) {}
        }
        throw Exception("Failed to fetch details")
    }

    private fun getAdditionals(data: List<JsonElement>): ShortHentai {
        fun Collection<JsonElement>.getTags(): List<Name> = this.map {
            Name(data[it.int + 2].string, data[it.int + 3].string)
        }
        val hentaiIndexes = data[1].parseAs<HentaiIndexes>()

        val hash = data[hentaiIndexes.hash].string
        val thumbnail = data[hentaiIndexes.thumbnail].int
        val description = data[hentaiIndexes.description].jsonPrimitive.contentOrNull

        val releasedAt = data[hentaiIndexes.releasedAt].string
        val createdAt = data[hentaiIndexes.createdAt].string
        val size = data[hentaiIndexes.size].long
        val pages = data[hentaiIndexes.pages].int

        val tags = data[hentaiIndexes.tags].array.ifEmpty { null }?.getTags()

        return ShortHentai(
            hash = hash,
            thumbnail = thumbnail,
            description = description,
            releasedAt = releasedAt,
            createdAt = createdAt,
            tags = tags,
            size = size,
            pages = pages,
        )
    }

    private fun Hentai.toSManga() = SManga.create().apply {
        title = this@toSManga.title
        url = "/g/$id?$pages&hash=$hash"
        author = tags?.filter { it.namespace == "circle" }?.joinToString { it.name }
        artist = tags?.filter { it.namespace == "artist" }?.joinToString { it.name }
        genre = tags?.filter { it.namespace == "tag" }?.joinToString { it.name }
        thumbnail_url = "$baseImageUrl/$hash/$thumbnail?type=cover"
        status = SManga.COMPLETED
    }

    private fun generateSeed(): String {
        val length = Random.nextInt(4, 9)
        val string = StringBuilder(length)
        repeat(length) {
            string.append(charset.random())
        }
        return string.toString()
    }

    companion object {
        private const val TMP_CDN_DOMAIN = "127.0.0.1"
        private const val TMP_CDN_URL = "http://$TMP_CDN_DOMAIN"
        private val ARCHIVE_REGEX = Regex("^/archive/(\\d+)/.*")

        private val dateReformat = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
        private val releasedAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
            .withZone(ZoneOffset.UTC)
        private val createdAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneOffset.UTC)
    }
}
