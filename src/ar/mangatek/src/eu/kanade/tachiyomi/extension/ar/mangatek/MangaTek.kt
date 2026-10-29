package eu.kanade.tachiyomi.extension.ar.mangatek

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MangaTek : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(SpeechBubblePainterInterceptor({ baseUrl }, id))
        rateLimit(3)
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()

        val mangas = document.select(".flex-grow .grid a").map { element ->
            SManga.create().apply {
                title = element.select("h3").attr("title")
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("nav a[aria-disabled=false] .fa-chevron-left") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-list?sort=views&page=$page")
        return response.toMangasPage()
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-list?page=$page")
        return response.toMangasPage()
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            }
            addQueryParameter("page", page.toString())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
        }.build()
        return client.get(url).toMangasPage()
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$API_BASE/api/tags?limit=500")
        return response.parseAs<JsonElement>()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<TagsResponse>()?.data
            .orEmpty()
            .filter { it.counter > 0 }
            .map { it.name }
            .distinct()
            .sorted()

        return FilterList(
            SortFilter(),
            StatusFilter(),
            GenreFilter(tags),
        )
    }

    // ========================= Details & Chapters  =========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() !in setOf("manga", "reader")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            this.url = "/manga/$slug"
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga).toHttpUrl()
        val data: MangaDto = client.get(url).asJsoup().extractAstroProp("manga")
        val slug = url.pathSegments[1]

        return SMangaUpdate(
            data.manga.toSManga(manga.url),
            data.manga.chapters.map { it.toSChapter(slug) },
        )
    }

    //  ============================== Astro ==============================

    private inline fun <reified T> Document.extractAstroProp(key: String): T {
        val prop = selectFirst("[props*=$key]")?.attr("props")
            ?: throw Exception("Unable to find prop with $key")
        return prop.parseAs<JsonElement>().unwrapAstro().parseAs()
    }

    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> when {
            size == 2 && this[0] is JsonPrimitive -> this[1].unwrapAstro()
            else -> JsonArray(map { it.unwrapAstro() })
        }
        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }

    //  ============================== Page ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val props: ChapterProps = document.extractAstroProp("imageUrls")

        val overlayData = props.overlayBlob?.let { OverlayCrypto.decryptHexBlob(it).parseAs<OverlayData>() }
            ?: unlockOverlay(document, props)

        // page_number counts from 1 after the overlayPageOffset images
        val firstOverlayImage = props.overlayPageOffset ?: 0
        val overlaysByImage = overlayData?.pages.orEmpty()
            .associate { firstOverlayImage + it.pageNumber - 1 to it.overlays }

        return props.imageUrls.mapIndexed { index, imageUrl ->
            val overlays = overlaysByImage[index]

            if (overlays.isNullOrEmpty()) {
                Page(index, imageUrl = imageUrl)
            } else {
                val url = imageUrl.toHttpUrl().newBuilder()
                    .fragment(overlays.toJsonString())
                    .build().toString()

                Page(index, imageUrl = url)
            }
        }
    }

    private suspend fun unlockOverlay(document: Document, props: ChapterProps): OverlayData? {
        if (props.textMode != "overlay") return null
        val chapterId = props.chapterId ?: return null
        val token = props.unlockToken ?: return null
        val seeds = readerSeeds(document)
        val proof = OverlayCrypto.proof(seeds.proofSalt, token, chapterId)
        val esid = OverlayCrypto.esid(seeds.readerSeed)
        val request = UnlockRequest(chapterId, token, proof, esid)

        var response = client.post(UNLOCK_API_URL, body = request.toJsonRequestBody()).parseAs<UnlockResponse>()
        val nonce = response.nonce
        if (response.needsChallenge && nonce != null) {
            val pow = response.powBits?.let { OverlayCrypto.pow(nonce, it) }
            val retry = UnlockRequest(chapterId, token, proof, esid, nonce, pow)
            response = client.post(UNLOCK_API_URL, body = retry.toJsonRequestBody()).parseAs<UnlockResponse>()
        }

        val overlay = response.overlay ?: return null
        return OverlayCrypto.decryptOverlay(seeds.readerSeed, overlay, response.ks, response.key, token, chapterId)
            ?.parseAs<OverlayData>()
    }

    @Volatile
    private var cachedSeeds: Pair<String, BundleSeeds.Seeds>? = null

    // The reader's seeds rotate with each build of its bundle
    private suspend fun readerSeeds(document: Document): BundleSeeds.Seeds {
        val bundleUrl = document.selectFirst("astro-island[component-url*=ChapterImageViewer]")
            ?.attr("abs:component-url")
            ?: throw Exception("Unable to find the reader script")
        cachedSeeds?.takeIf { it.first == bundleUrl }?.let { return it.second }

        val script = client.get(bundleUrl).body.string()
        val seeds = BundleSeeds.extract(script)
        cachedSeeds = bundleUrl to seeds
        return seeds
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    companion object {
        val PAGE_REGEX = Regex(""".*?\.(webp|png|jpg|jpeg)(?:\?[^#]*)?#\[.*?]""", RegexOption.IGNORE_CASE)
        private const val API_BASE = "https://api.mangatek.com"
        private const val UNLOCK_API_URL = "$API_BASE/api/reader/unlock"
    }
}
