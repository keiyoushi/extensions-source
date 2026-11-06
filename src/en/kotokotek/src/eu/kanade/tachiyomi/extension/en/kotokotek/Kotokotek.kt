package eu.kanade.tachiyomi.extension.en.kotokotek

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@Source
abstract class Kotokotek : KeiSource() {

    override val supportsLatest = false

    // An archive of a handful of comics, all listed in the site menu
    override suspend fun getPopularManga(page: Int): MangasPage = coroutineScope {
        val paths = client.get("$baseUrl$SITE_PATH", Fetch.DOCUMENT).asJsoup()
            .select("li:has(> div a[href$='/comics']) ul a[href]")
            .map { it.absUrl("href").toHttpUrl().encodedPath }
        val mangas = paths.map { path ->
            async { mangaDetailsParse(client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup()).apply { url = path } }
        }.awaitAll()
        MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getPopularManga(1).mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val series = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val path = "$SITE_PATH/$series"
        val document = client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("a[href*='$path/']") == null) return null
        return mangaDetailsParse(document).apply { this.url = path }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select("a[href*='${manga.url}/']")
            .map { it.absUrl("href").toHttpUrl().encodedPath }
            .distinct()
            .map { path ->
                SChapter.create().apply {
                    url = path
                    name = path.substringAfterLast('/').replace('-', ' ').replaceFirstChar(Char::uppercase)
                }
            }
            .reversed()
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, chapterList)
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("main h1")!!.text().removeSuffix(" episodes")
        thumbnail_url = document.selectFirst("main [data-image-info]")?.attr("data-image-info")
            ?.let { IMAGE_URI_REGEX.find(it)?.groupValues?.get(1) }
            ?.let { "$MEDIA_URL/$it" }
        author = "Kotokot"
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // The episode pages only render their first images, the rest comes from Wix's dynamic pages router
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val prefix = chapter.url.removePrefix("$SITE_PATH/").substringBefore('/')
        val router = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("script#wix-viewer-model")!!.data()
            .parseAs<ViewerModel>()
            .siteFeaturesConfigs.dynamicPages.prefixToRouterFetchData.getValue(prefix)

        val bodyData = router.optionsData.bodyData
        val request = RouterRequest(
            urlParams = router.urlData.queryParams.split('&').associate { it.substringBefore('=') to it.substringAfter('=') },
            body = RouterRequestBody(
                pageRoles = bodyData.pageRoles,
                routerPrefix = bodyData.routerPrefix,
                config = bodyData.config,
                roleVariations = bodyData.roleVariations,
                requestInfo = RequestInfo(env = "browser", formFactor = "mobile"),
                routerSuffix = chapter.url.removePrefix("$SITE_PATH/$prefix"),
                fullUrl = chapterUrl,
            ),
        )
        val url = "${router.urlData.basePath}/pages".toHttpUrl().newBuilder()
            .addEncodedQueryParameter(".r", request.toJsonString().gzipBase64Url())
            .build()

        return client.get(url, Fetch.XHR.from(chapterUrl)).parseAs<RouterResponse>()
            .result.data.items.first().mediagallery
            .mapIndexed { index, media ->
                Page(index, imageUrl = "$MEDIA_URL/${media.src.removePrefix("wix:image://v1/").substringBefore('/')}")
            }
    }

    private fun String.gzipBase64Url(): String {
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(toByteArray()) } }.toByteArray()
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        private const val SITE_PATH = "/kotokot-webcomics"
        private const val MEDIA_URL = "https://static.wixstatic.com/media"
        private val IMAGE_URI_REGEX = Regex(""""uri":"([^"]+)"""")
    }
}
