package eu.kanade.tachiyomi.extension.pt.sakuramangas

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.pt.sakuramangas.security.SecurityConfig
import eu.kanade.tachiyomi.extension.pt.sakuramangas.security.SecurityHeaders
import eu.kanade.tachiyomi.extension.pt.sakuramangas.security.YggdrasilCipher
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import kotlin.concurrent.thread

class SakuraMangas : HttpSource(), ConfigurableSource {
    override val lang = "pt-BR"

    override val supportsLatest = true

    override val name = "Sakura Mangás"

    override val baseUrl = "https://sakuramangas.org"

    private val preferences = getPreferences()

    override val client = network.cloudflareClient.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(2)
        .build()

    private var genresSet: Set<Genre> = emptySet()
    private var demographyOptions: List<Pair<String, String>> = listOf(
        "Todos" to "",
    )
    private var classificationOptions: List<Pair<String, String>> = listOf(
        "Todos" to "",
    )
    private var orderByOptions: List<Pair<String, String>> = listOf(
        "Lidos" to "3",
    )

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .set("Sec-CH-UA-Mobile", "?1")
        .set("Sec-CH-UA-Platform", "\"Android\"")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-User", "?1")
        .set("Upgrade-Insecure-Requests", "1")

    private fun ajaxHeadersBuilder() = headersBuilder()
        .set("Accept", "application/json, text/javascript, */*; q=0.01")
        .set("Origin", baseUrl)
        .set("X-Requested-With", "XMLHttpRequest")
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-origin")
        .removeAll("Sec-Fetch-User")
        .removeAll("Upgrade-Insecure-Requests")

    private val ajaxHeaders: okhttp3.Headers by lazy { ajaxHeadersBuilder().build() }

    // ================================ Popular =======================================

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/dist/sakura/models/home/__.home_ultimos.php", ajaxHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<List<SakuraLatestMangaDto>>()
        val mangas = result.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("search", query)
            .add("order", "3")
            .add("offset", ((page - 1) * 15).toString())
            .add("limit", "15")

        val inclGenres = mutableListOf<String>()
        val exclGenres = mutableListOf<String>()

        var demography: String? = null
        var classification: String? = null
        var orderBy: String? = null

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach {
                    when (it.state) {
                        Filter.TriState.STATE_INCLUDE -> inclGenres.add(it.id)
                        Filter.TriState.STATE_EXCLUDE -> exclGenres.add(it.id)
                        else -> {}
                    }
                }

                is DemographyFilter -> demography = filter.getValue().ifEmpty { null }
                is ClassificationFilter -> classification = filter.getValue().ifEmpty { null }
                is OrderByFilter -> orderBy = filter.getValue().ifEmpty { null }
                else -> {}
            }
        }

        inclGenres.forEach { form.add("tags[]", it) }
        exclGenres.forEach { form.add("excludeTags[]", it) }

        demography?.let { form.add("demography", it) }
        classification?.let { form.add("classification", it) }
        orderBy?.let { form.add("order", it) }

        return POST("$baseUrl/dist/sakura/models/obras/__.obras__buscar.php", ajaxHeaders, form.build())
    }

    fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".h5-titulo")!!.text()
        thumbnail_url = element.selectFirst("img.img-pesquisa")?.absUrl("src")
        description = element.selectFirst(".p-sinopse")?.text()

        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SakuraMangasResultDto>()
        val document = result.asJsoup("$baseUrl/obras/")
        val seriesList = document.select(".result-item").map(::searchMangaFromElement)
        return MangasPage(seriesList, result.hasMore)
    }

    // ================================ Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    private fun mangaDetailsApiRequest(mangaId: String, challenge: String, token: String, securityKey: Long): Request {
        val proof = SecurityHeaders.generateHeaderProof(challenge, securityKey, headers["User-Agent"], keys.securityScript)
            ?: throw Error("Failed to generate header proof")

        val form = FormBody.Builder()
            .add("manga_id", mangaId)
            .add("dataType", "json")
            .add("challenge", challenge)
            .add("proof", proof)

        val detailsHeaders = SecurityHeaders.buildSecuredAjaxHeaders(ajaxHeadersBuilder(), keys, token)

        return POST("$baseUrl/dist/sakura/models/manga/.__obf__manga_info.php", detailsHeaders, form.build())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("meta[manga-id]")!!.attr("manga-id")
        val challenge = document.selectFirst("meta[name=header-challenge]")!!.attr("content")
        val token = document.selectFirst("meta[name=csrf-token]")!!.attr("content")

        val securityKeyName = document.selectFirst("meta[name=security-key-name]")?.attr("content")
        val securityKey = SecurityConfig.getSecurityKeyForContext(securityKeyName, keys)

        return client.newCall(mangaDetailsApiRequest(mangaId, challenge, token, securityKey)).execute()
            .also { response ->
            }
            .parseAs<SakuraMangaInfoDto>().toSManga(document.baseUri())
    }

    private val keys: SecurityConfig.Keys by lazy {
        SecurityConfig.extractKeys(baseUrl, client, headers)
    }

    // ================================ Chapters =======================================

    private fun chapterListApiRequest(mangaId: String, challenge: String, token: String, securityKey: Long, page: Int): Request {
        val proof = SecurityHeaders.generateHeaderProof(challenge, securityKey, headers["User-Agent"], keys.securityScript)
            ?: throw Error("Failed to generate header proof")

        val form = FormBody.Builder()
            .add("manga_id", mangaId)
            .add("offset", ((page - 1) * 90).toString())
            .add("order", "desc")
            .add("limit", "90")
            .add("challenge", challenge)
            .add("proof", proof)

        val chapterHeaders = SecurityHeaders.buildSecuredAjaxHeaders(ajaxHeadersBuilder(), keys, token)

        return POST("$baseUrl/dist/sakura/models/manga/.__obf__manga_capitulos.php", chapterHeaders, form.build())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("meta[manga-id]")!!.attr("manga-id")
        val challenge = document.selectFirst("meta[name=header-challenge]")!!.attr("content")
        val token = document.selectFirst("meta[name=csrf-token]")!!.attr("content")

        val securityKeyName = document.selectFirst("meta[name=security-key-name]")?.attr("content")
        val securityKey = SecurityConfig.getSecurityKeyForContext(securityKeyName, keys)

        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val apiResponse = client.newCall(chapterListApiRequest(mangaId, challenge, token, securityKey, page++)).execute()
                .also { resp ->
                }
                .parseAs<SakuraChapterListResponseDto>()

            val chapterGroup = apiResponse.data.flatMap { it.toSChapterList() }.also {
                chapters += it
            }

            if (!apiResponse.hasMore) break
        } while (chapterGroup.isNotEmpty())

        return chapters
    }

    // ================================ Pages =======================================

    private fun pageListApiRequest(
        chapterId: String,
        token: String,
        securityKey: Long,
        challenge: String,
        csrf: String,
    ): Request {
        val proof = SecurityHeaders.generateHeaderProof(challenge, securityKey, headers["User-Agent"], keys.securityScript)
            ?: throw Error("Failed to generate header proof")

        val form = FormBody.Builder()
            .add("chapter_id", chapterId)
            .add("token", token)
            .add("challenge", challenge)
            .add("proof", proof)

        val pageHeaders = SecurityHeaders.buildSecuredAjaxHeaders(ajaxHeadersBuilder(), keys, csrf)

        return POST(
            "$baseUrl/dist/sakura/models/capitulo/__obf__capitulos__read.php",
            pageHeaders,
            form.build(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val chapterId = document.selectFirst("meta[chapter-id]")!!.attr("chapter-id")
        val token = document.selectFirst("meta[token]")!!.attr("token")
        val subtoken = document.selectFirst("meta[token]")!!.attr("subtoken")
        val challenge = document.selectFirst("meta[name=header-challenge]")!!.attr("content")
        val csrf = document.selectFirst("meta[name=csrf-token]")!!.attr("content")

        val securityKeyName = document.selectFirst("meta[name=security-key-name]")?.attr("content")
        val securityKey = SecurityConfig.getSecurityKeyForContext(securityKeyName, keys)

        val apiResponse = client.newCall(pageListApiRequest(chapterId, token, securityKey, challenge, csrf)).execute()
            .parseAs<SakuraMangaChapterReadResponseDto>()

        val baseUrl = document.baseUri().trimEnd('/')

        val imageUrls: List<String> = decryptChapterResponse(apiResponse.data, subtoken)

        return imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = "$baseUrl/$url".toHttpUrl().toString())
        }
    }

    private fun decryptChapterResponse(data: SakuraMangaChapterDataDto, subtoken: String): List<String> {
        val encryptedKey = data.encryptedEphemeralKey
            ?: throw Error("Missing encrypted ephemeral key")
        val encryptedImageKey = data.encryptedImageKey
            ?: throw Error("Missing encrypted image key")
        val encryptedUrls = data.encryptedUrls
            ?: throw Error("Missing encrypted URLs")

        val cipher = encryptedKey.cipher.uppercase()

        val ephemeralKey = YggdrasilCipher.decipher(cipher, encryptedKey.payload, subtoken)
            ?: throw Error("Unsupported cipher: $cipher. Supported: ${YggdrasilCipher.supportedCiphers.joinToString()}")

        val imageKey = SecurityHeaders.xorDecrypt(encryptedImageKey, ephemeralKey)

        return SecurityHeaders.xorDecrypt(encryptedUrls, imageKey).parseAs()
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "")
            .set("x-requested-with", "SakuraMatchClient")
            .set("x-signature-version", "v5-fetch-custom")
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(): FilterList {
        thread {
            fetchFilters()
        }

        return FilterList(
            OrderByFilter("Ordenar por", orderByOptions, "order"),
            DemographyFilter("Demografia", demographyOptions, "demography"),
            ClassificationFilter("Classificação", classificationOptions, "classification"),
            GenreList(
                title = "Gêneros",
                genres = genresSet.toTypedArray(),
            ),
        )
    }

    private fun fetchFilters() {
        if (genresSet.isNotEmpty()) {
            return
        }

        try {
            val document = client
                .newCall(GET("$baseUrl/obras/", headers))
                .execute()
                .asJsoup()

            genresSet = document.select(".genero-badge").map { element ->
                val id = element.attr("data-value")
                Genre(element.ownText(), id)
            }.toSet()

            val demoOpts = document.select("select#demografia-select option").mapNotNull { opt ->
                val value = opt.attr("value").orEmpty()
                val text = opt.text()
                if (text.isEmpty()) null else text to value
            }
            if (demoOpts.isNotEmpty()) demographyOptions = demoOpts

            val classOpts =
                document.select("select#classificacao-select option").mapNotNull { opt ->
                    val value = opt.attr("value").orEmpty()
                    val text = opt.text()
                    if (text.isEmpty()) null else text to value
                }
            if (classOpts.isNotEmpty()) classificationOptions = classOpts

            val orderOptions = document.select("select#ordenar-por option").mapNotNull { opt ->
                val value = opt.attr("value").orEmpty()
                val text = opt.text()
                if (text.isEmpty()) null else text to value
            }
            if (orderOptions.isNotEmpty()) orderByOptions = orderOptions
        } catch (_: Exception) {
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }
}
