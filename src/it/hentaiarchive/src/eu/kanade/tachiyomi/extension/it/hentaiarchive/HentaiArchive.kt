package eu.kanade.tachiyomi.extension.it.hentaiarchive

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class HentaiArchive : KeiSource() {

    private val cdnHeaders: Headers
        get() = headers.newBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        if (url.contains("picsarchive1.b-cdn.net")) {
            return@addInterceptor chain.proceed(request.newBuilder().headers(cdnHeaders).build())
        }
        chain.proceed(request)
    }

    override val supportsLatest = false

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/category/hentai-recenti/page/$page").asJsoup()

        val mangas = document.select("div.posts-container article").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.entire-meta-link")!!.absUrl("href"))
                title = element.selectFirst("span.screen-reader-text")!!.text()
                thumbnail_url = element.selectFirst("span.post-featured-img img.wp-post-image")?.absUrl("data-nectar-img-src")
            }
        }

        val hasNextPage = document.selectFirst("nav#pagination a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        val document = client.get(url).asJsoup()

        val mangas = document.select("article.result").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("h2.title a")!!.absUrl("href"))
                title = element.selectFirst("h2.title a")!!.text()
                thumbnail_url = element.selectFirst("a img.wp-post-image")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) {
            val document = client.get(getMangaUrl(manga)).asJsoup()

            SManga.create().apply {
                url = manga.url
                status = SManga.COMPLETED
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                with(document.selectFirst("div.main-content")!!) {
                    title = selectFirst("h1")!!.text()
                    genre = getInfo("meta-category")
                }
            }
        } else {
            manga
        }

        val chapter = SChapter.create().apply {
            url = manga.url
            chapter_number = 1F
            name = "Chapter"
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    private fun Element.getInfo(text: String): String? {
        // Extract class names from elements with the class 'meta-category'
        return select(".$text")
            .flatMap { it.children() }
            .flatMap { it.classNames() }
            .joinToString(", ")
            .replace("-", " ")
            .replace("hentai", "")
            .trim()
            .takeUnless(String::isEmpty)
    }

    // =============================== Pages ================================
    private val imageRegex = Regex("-(\\d+x\\d+)(?=\\.jpg)")

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("src") -> absUrl("src")
        else -> ""
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("div.content-inner img:not(.sp-banner img)").mapIndexed { index, element ->
            val imageUrl = element.imgAttr().replace(imageRegex, "")
            Page(index, imageUrl = imageUrl)
        }
    }
}
