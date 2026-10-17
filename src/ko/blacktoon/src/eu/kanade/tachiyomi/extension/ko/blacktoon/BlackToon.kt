package eu.kanade.tachiyomi.extension.ko.blacktoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okio.IOException
import kotlin.math.min
import kotlin.random.Random

@Source
abstract class BlackToon : KeiSource() {

    private var currentBaseUrlHost = ""

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        if (currentBaseUrlHost.isBlank()) {
            noRedirectClient.newCall(GET(baseUrl, headers)).execute().use {
                currentBaseUrlHost = it.headers["location"]?.toHttpUrlOrNull()?.host
                    ?: throw IOException("unable to get updated url")
            }
        }

        val request = chain.request().newBuilder().apply {
            if (chain.request().url.toString().startsWith(baseUrl)) {
                url(
                    chain.request().url.newBuilder()
                        .host(currentBaseUrlHost)
                        .build(),
                )
            }
            header("Referer", "https://$currentBaseUrlHost/")
            header("Origin", "https://$currentBaseUrlHost")
        }.build()

        return@addInterceptor chain.proceed(request)
    }

    private val noRedirectClient by lazy {
        network.client.newBuilder()
            .followRedirects(false)
            .build()
    }

    private var db: List<SeriesItem>? = null
    private val dbMutex = Mutex()

    private suspend fun getDb(): List<SeriesItem> = db ?: dbMutex.withLock {
        db ?: fetchDb().also { db = it }
    }

    // Data scripts, chapter lists and images are served from separate hosts that the site
    // declares in inline scripts (inc_url1/inc_url2) and /data/config.js (img_domain).
    private class Hosts(val toonList: String, val webtoon: String, val image: String)

    private var hosts: Hosts? = null
    private val hostsMutex = Mutex()

    private suspend fun getHosts(): Hosts = hosts ?: hostsMutex.withLock {
        hosts ?: run {
            val home = client.get(baseUrl).use { it.body.string() }
            val config = client.get("$baseUrl/data/config.js").use { it.body.string() }
            Hosts(
                toonList = INC_URL1_REGEX.find(home)!!.groupValues[1],
                webtoon = INC_URL2_REGEX.find(home)!!.groupValues[1],
                image = IMG_DOMAIN_REGEX.find(config)!!.groupValues[1].removeSuffix("/") + "/",
            )
        }.also { hosts = it }
    }

    private suspend fun fetchDb(): List<SeriesItem> {
        val hosts = getHosts()
        return listOf(0, 1).flatMap { listIdx ->
            client.get("${hosts.webtoon}/webtoon_$listIdx.js")
                .use { it.body.string() }
                .substringAfter(" = ")
                .removeSuffix(";")
                .parseAs<List<SeriesItem>>()
                .onEach { it.listIndex = listIdx }
        }
    }

    private suspend fun List<SeriesItem>.getPageChunk(page: Int): MangasPage = MangasPage(
        mangas = subList((page - 1) * 24, min(page * 24, size))
            .map { it.toSManga(getHosts().image) },
        hasNextPage = (page + 1) * 24 <= size,
    )

    override suspend fun getPopularManga(page: Int): MangasPage = getDb().sortedByDescending { it.hot }.getPageChunk(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getDb().sortedByDescending { it.updatedAt }.getPageChunk(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var list = getDb()

        if (query.isNotBlank()) {
            val stdQuery = query.trim()
            list = list.filter {
                it.name.contains(stdQuery, true) ||
                    it.author.contains(stdQuery, true)
            }
        }

        filters.filterIsInstance<ListFilter>().forEach {
            list = it.applyFilter(list)
        }

        return list.getPageChunk(page)
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override fun getMangaUrl(manga: SManga): String = buildString {
        if (currentBaseUrlHost.isBlank()) {
            append(baseUrl)
        } else {
            append("https://")
            append(currentBaseUrlHost)
        }
        append("/webtoon/")
        append(manga.url)
        append(".html")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchDetails(manga: SManga): SManga {
        val doc = client.get("$baseUrl/webtoon/${manga.url}.html").asJsoup()
        return SManga.create().apply {
            title = manga.title
            description = doc.select("p.mt-2").last()?.text()
            thumbnail_url = doc.selectFirst("img.thumb2[o_src]")?.let { getHosts().image + it.attr("o_src") }
                ?: manga.thumbnail_url
            status = manga.status
        }
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val url = "${getHosts().toonList}/data/toonlist/${manga.url}.js?v=${"%.17f".format(Random.nextDouble())}"

        val data = client.get(url).parseAs<List<Chapter>> {
            it.substringAfter(" = ").removeSuffix(";")
        }

        return data.map { it.toSChapter(manga.url) }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = buildString {
        if (currentBaseUrlHost.isBlank()) {
            append(baseUrl)
        } else {
            append("https://")
            append(currentBaseUrlHost)
        }
        append("/webtoons/")
        append(chapter.url)
        append(".html")
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl/webtoons/${chapter.url}.html").asJsoup()
        val imageHost = getHosts().image

        return document.select("#toon_content_imgs img").mapIndexed { i, img ->
            Page(i, imageUrl = imageHost + img.attr("o_src"))
        }
    }

    companion object {
        private val INC_URL1_REGEX = Regex("""inc_url1\s*=\s*"([^"]+)"""")
        private val INC_URL2_REGEX = Regex("""inc_url2\s*=\s*"([^"]+)"""")
        private val IMG_DOMAIN_REGEX = Regex("""var img_domain\s*=\s*"([^"]+)"""")
    }
}
