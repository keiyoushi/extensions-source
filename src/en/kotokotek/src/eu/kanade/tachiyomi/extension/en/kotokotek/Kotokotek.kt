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
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@Source
abstract class Kotokotek : KeiSource() {

    override val supportsLatest = false

    // The author stopped posting on the site in 2024, so its public comics are a fixed list. Each Wix page
    // weighs about 1 MB, and the menu that links them only exists in the mobile layout.
    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(COMICS.map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = COMICS.filter { it.title.contains(query, ignoreCase = true) }.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return COMICS.find { it.slug == slug }?.toSManga()
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
    override suspend fun getPageList(chapter: SChapter): List<Page> = coroutineScope {
        val chapterUrl = getChapterUrl(chapter)
        val prefix = chapter.url.removePrefix("$SITE_PATH/").substringBefore('/')
        val tokens = async { client.get("$baseUrl$SITE_PATH/_api/v1/access-tokens", Fetch.XHR.from(chapterUrl)).parseAs<AccessTokens>() }
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

        val routerHeaders = headers.newBuilder()
            .set("Authorization", tokens.await().apps.getValue(router.urlData.appDefinitionId).instance)
            .build()

        client.get(url, routerHeaders, Fetch.XHR.from(chapterUrl)).parseAs<RouterResponse>()
            .result.data.items.first().mediagallery
            .mapIndexed { index, media ->
                Page(index, imageUrl = "$MEDIA_URL/${media.src.removePrefix("wix:image://v1/").substringBefore('/')}")
            }
    }

    private fun String.gzipBase64Url(): String {
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(toByteArray()) } }.toByteArray()
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private class Comic(val slug: String, val title: String, private val cover: String) {
        fun toSManga() = SManga.create().apply {
            url = "$SITE_PATH/$slug"
            title = this@Comic.title
            thumbnail_url = "$MEDIA_URL/$cover"
        }
    }

    companion object {
        private const val SITE_PATH = "/kotokot-webcomics"
        private const val MEDIA_URL = "https://static.wixstatic.com/media"
        private val IMAGE_URI_REGEX = Regex(""""uri":"([^"]+)"""")

        // The site's Comics menu, in its order. The Uncensored Comics menu is for paying members only.
        private val COMICS = listOf(
            Comic("swcensor", "Safeword is Handcuffs", "cd69cd_9fc58e0c8bda4357abd8d3df2f14b6c7~mv2.jpg"),
            Comic("di-censor", "Demon Inside", "cd69cd_4a15d2e80b58401ab4e787e1025c4307~mv2.jpg"),
            Comic("hccensor", "Handcuffs (old)", "cd69cd_9c9a525b647b4095ab388f7b99902381~mv2.jpg"),
            Comic("gdcensor", "Guard Dog", "cd69cd_42dfa3efb91e45608e46558211b2545c~mv2.jpg"),
            Comic("items-4", "Handcuffs remastered", "cd69cd_d63f6d0dd6e14695813091650315524e~mv2.jpg"),
            Comic("soecensor", "Surprise of Eternity", "cd69cd_a4e59577f1f84baf93fcc3819634988c~mv2.jpg"),
        )
    }
}
