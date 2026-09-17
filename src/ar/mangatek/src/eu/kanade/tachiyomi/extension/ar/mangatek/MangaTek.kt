package eu.kanade.tachiyomi.extension.ar.mangatek

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class MangaTek : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(SpeechBubblePainterInterceptor())
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
            addQueryParameter("search", query)
            addQueryParameter("page", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    // ========================= Details & Chapters  =========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        check(url.pathSegments.size >= 2) { "Unsupported URL" }
        val slug = url.pathSegments[1]
        val manga = SManga.create().apply {
            this.url = "/manga/$slug"
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$baseUrl${manga.url}".toHttpUrl()
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
        val overlaysByPageNumber: Map<Int, OverlayPage> = props.overlayBlob
            ?.let(::decrypt)?.pages
            ?.associateBy { it.pageNumber } ?: emptyMap()

        return props.imageUrls.mapIndexed { index, imageUrl ->
            val overlayPage = overlaysByPageNumber[index]
                ?: return@mapIndexed Page(index, imageUrl = imageUrl)

            val url = imageUrl.toHttpUrl().newBuilder()
                .fragment(overlayPage.overlays.toJsonString())
                .build().toString()

            Page(index, imageUrl = url)
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    // decrypt

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string length" }

        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun decrypt(blob: String): OverlayData {
        val (ivHex, ctHex, tagHex) = blob.split(":").also {
            require(it.size == 3) { "unexpected overlayBlob format" }
        }

        val iv = ivHex.hexToBytes()
        val ciphertext = ctHex.hexToBytes()
        val tag = tagHex.hexToBytes()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(KEY.hexToBytes(), "AES"),
                GCMParameterSpec(tag.size * 8, iv),
            )
        }

        return String(cipher.doFinal(ciphertext + tag), Charsets.UTF_8).parseAs<OverlayData>()
    }

    companion object {
        val PAGE_REGEX = Regex(""".*?\.(webp|png|jpg|jpeg)(?:\?v=\d+)?#\[.*?]""", RegexOption.IGNORE_CASE)
        private val KEY = "ff453871399fe268588a0936b45376022d85ed0fd1292001d5102f6a30291dc1"
    }
}
