package eu.kanade.tachiyomi.extension.es.hentaimode

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
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class HentaiMode : KeiSource() {
    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrlHost }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get(baseUrl).asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.row div[class*=\"book-list\"] > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".book-description > p")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) return null
        val id = url.pathSegments.getOrNull(1) ?: return null

        val document = client.get("$baseUrl/g/$id").asJsoup()
        return mangaDetailsParse(document).apply {
            setUrlWithoutDomain(document.location())
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        require(query.length >= 3) { "Please use at least 3 characters!" }

        val url = "$baseUrl/buscar".toHttpUrl()
            .newBuilder()
            .addQueryParameter("s", query)
            .build()
        return parseMangaList(client.get(url).asJsoup())
    }

    // =========================== Manga Details ============================
    private val additionalInfos = listOf("Serie", "Tipo", "Personajes", "Idioma")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) {
            mangaDetailsParse(client.get(getMangaUrl(manga)).asJsoup())
        } else {
            manga
        }

        val chapter = SChapter.create().apply {
            url = manga.url.replace("/g/", "/leer/")
            chapter_number = 1F
            name = "Chapter"
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.selectFirst("div#cover img")?.absUrl("src")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        with(document.selectFirst("div#info-block > div#info")!!) {
            title = selectFirst("h1")!!.text()
            genre = getInfo("Categorías")
            author = getInfo("Grupo")
            artist = getInfo("Artista")

            description = buildString {
                additionalInfos.forEach { info ->
                    getInfo(info)?.also {
                        append(info)
                        append(": ")
                        append(it)
                        append("\n")
                    }
                }
            }
        }
    }

    private fun Element.getInfo(text: String): String? = select("div.tag-container:containsOwn($text) a.tag")
        .joinToString { it.text() }
        .takeIf(String::isNotEmpty)

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val script = document.selectFirst("script:containsData(page_image)")!!.data()
        val pagePaths = script.substringAfter("pages = [")
            .substringBefore(",]")
            .substringBefore("]") // Just to make sure
            .split(',')
            .map {
                it.substringAfter(":").substringAfter('"').substringBefore('"')
            }

        return pagePaths.mapIndexed { index, path ->
            Page(index, imageUrl = path)
        }
    }
}
