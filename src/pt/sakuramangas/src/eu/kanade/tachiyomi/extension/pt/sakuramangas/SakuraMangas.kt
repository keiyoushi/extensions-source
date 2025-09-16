package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.security.MessageDigest
import java.util.Calendar
import kotlin.concurrent.thread

class SakuraMangas : HttpSource() {
    override val lang = "pt-BR"

    override val supportsLatest = true

    override val name = "Sakura Mangás"

    override val baseUrl = "https://sakuramangas.org"

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

    private var csrfToken: String? = null

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::csrfTokenInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")
        .set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,fr;q=0.6,zh-CN;q=0.5,zh-TW;q=0.4,zh;q=0.3")
        .set("Connection", "keep-alive")
        .set("X-Requested-With", "XMLHttpRequest")

    private fun csrfTokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Only process if it's GET and the URL contains '/obras/'
        if (request.method != "GET" || !request.url.toString().contains("/obras/")) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        // Only process if it's an HTML response
        if (response.header("Content-Type")?.contains("text/html") == true) {
            try {
                val document = response.peekBody(Long.MAX_VALUE).string()
                val jsoupDoc = Jsoup.parse(document)

                // Use Jsoup to find the csrf-token meta tag
                val csrfMeta = jsoupDoc.selectFirst("meta[name=csrf-token]")
                csrfMeta?.let { meta ->
                    val token = meta.attr("content")
                    if (token.isNotEmpty()) {
                        csrfToken = token
                    }
                }
            } catch (error: Exception) {
                Log.e("SakuraMangas", "Failed to find csrf-token", error)
            }
        }

        return response
    }

    // ================================ Popular =======================================

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ================================ Latest =======================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/dist/sakura/models/home/__.home_ultimos.php", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<List<String>>()

        val mangas = result.map {
            val element = Jsoup.parseBodyFragment(it, baseUrl)
            SManga.create().apply {
                title = element.selectFirst(".h5-titulo")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("search", query)
            .add("order", "3")
            .add("offset", ((page - 1) * DEFAULT_LIMIT).toString())
            .add("limit", DEFAULT_LIMIT.toString())

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

        return POST("$baseUrl/dist/sakura/models/obras/__.obras_buscar.php", headers, form.build())
    }

    fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".h5-titulo")!!.text()
        thumbnail_url = element.selectFirst("img.img-pesquisa")?.absUrl("src")
        description = element.selectFirst(".p-sinopse")?.text()

        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SakuraMangasResultDto>()
        val seriesList =
            result.asJsoup("$baseUrl/obras/").select(".result-item").map(::searchMangaFromElement)
        return MangasPage(seriesList, result.hasMore)
    }

    // ================================ Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    private fun mangaDetailsApiRequest(
        mangaId: String,
        challenge: String?,
    ): Request {
        val form = FormBody.Builder()
            .add("manga_id", mangaId)
            .add("dataType", "json")

        val proof = this.generateHeaderProof(challenge, PROOF_INFO_SEED)
        if (!challenge.isNullOrBlank() && !proof.isNullOrBlank()) {
            form.add("challenge", challenge)
            form.add("proof", proof)
        }

        val headers = generateRequestHeaders()

        return POST(
            "$baseUrl/dist/sakura/models/manga/__obf__manga_info.php",
            headers,
            form.build(),
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("meta[manga-id]")!!.attr("manga-id")
        val challenge = document.selectFirst("meta[name=header-challenge]")?.attr("content")

        return client.newCall(mangaDetailsApiRequest(mangaId, challenge)).execute()
            .parseAs<SakuraMangaInfoDto>().toSManga(document.baseUri())
    }

    // ================================ Chapters =======================================

    private fun chapterListApiRequest(
        mangaId: String,
        page: Int,
        challenge: String?,
    ): Request {
        val form = FormBody.Builder()
            .add("manga_id", mangaId)
            .add("offset", ((page - 1) * CHAPTER_LIMIT).toString())
            .add("order", "desc")
            .add("limit", CHAPTER_LIMIT.toString())

        val proof = this.generateHeaderProof(challenge, PROOF_INFO_SEED)
        if (!challenge.isNullOrBlank() && !proof.isNullOrBlank()) {
            form.add("challenge", challenge)
            form.add("proof", proof)
        }

        val headers = generateRequestHeaders()

        return POST(
            "$baseUrl/dist/sakura/models/manga/__obf__manga_capitulos.php",
            headers,
            form.build(),
        )
    }

    override fun chapterListRequest(manga: SManga): Request {
        // Small delay to avoid CSRF token race conditions.
        // Reason: manga info and chapter list can be fetched in parallel, and the HTML
        // request that parses the CSRF may update the token between requests. This short
        // pause helps ensure the interceptor has already captured and updated the latest
        // CSRF token before we proceed with the next request.
        Thread.sleep(1000)
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("meta[manga-id]")!!.attr("manga-id")
        val challenge = document.selectFirst("meta[name=header-challenge]")?.attr("content")

        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val doc = client.newCall(chapterListApiRequest(mangaId, page++, challenge))
                .execute()
                .asJsoup()

            val chapterGroup = doc.select(".capitulo-item").map(::chapterFromElement).also {
                chapters += it
            }
        } while (chapterGroup.isNotEmpty())

        return chapters
    }

    fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = buildString {
            element.selectFirst(".num-capitulo")
                ?.text()
                ?.let { append(it) }

            element.selectFirst(".cap-titulo")
                ?.text()
                ?.takeIf { it.isNotBlank() }
                ?.let { append(" - $it") }
        }
        scanlator = element.selectFirst(".scan-nome")?.text()
        chapter_number =
            element
                .selectFirst(".num-capitulo")!!
                .attr("data-chapter")
                .toFloatOrNull() ?: 1F
        date_upload = element.selectFirst(".cap-data")?.text()?.toDate() ?: 0L
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // ================================ Pages =======================================

    private fun pageListApiRequest(
        chapterId: String,
        token: String,
        challenge: String?,
    ): Request {
        val form = FormBody.Builder()
            .add("chapter_id", chapterId)
            .add("token", token)

        val proof = this.generateHeaderProof(challenge, PROOF_CHAPTER_SEED)
        if (!challenge.isNullOrBlank() && !proof.isNullOrBlank()) {
            form.add("challenge", challenge)
            form.add("proof", proof)
        }

        val headers = generateRequestHeaders()

        return POST(
            "$baseUrl/dist/sakura/models/capitulo/__obf__capltulos_read.php",
            headers,
            form.build(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val chapterId = document.selectFirst("meta[chapter-id]")!!.attr("chapter-id")
        val token = document.selectFirst("meta[token]")!!.attr("token")
        val subtoken = document.selectFirst("meta[subtoken]")!!.attr("subtoken")
        val challenge = document.selectFirst("meta[name=header-challenge]")?.attr("content")

        val json =
            client.newCall(pageListApiRequest(chapterId, token, challenge)).execute()
                .parseAs<SakuraMangaChapterReadDto>()

        val baseUrl = document.baseUri().trimEnd('/')

        return json.getUrls(subtoken).mapIndexed { index, url ->
            Page(
                index,
                imageUrl = "$baseUrl/$url".toHttpUrl().toString(),
            )
        }
    }

    override fun imageUrlParse(response: Response): String = ""

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
                val text = opt.text().trim()
                if (text.isEmpty()) null else text to value
            }
            if (demoOpts.isNotEmpty()) demographyOptions = demoOpts

            val classOpts =
                document.select("select#classificacao-select option").mapNotNull { opt ->
                    val value = opt.attr("value").orEmpty()
                    val text = opt.text().trim()
                    if (text.isEmpty()) null else text to value
                }
            if (classOpts.isNotEmpty()) classificationOptions = classOpts

            val orderOptions = document.select("select#ordenar-por option").mapNotNull { opt ->
                val value = opt.attr("value").orEmpty()
                val text = opt.text().trim()
                if (text.isEmpty()) null else text to value
            }
            if (orderOptions.isNotEmpty()) orderByOptions = orderOptions
        } catch (e: Exception) {
            Log.e("SakuraMangas", "failed to fetch genres", e)
        }
    }

    private fun String.toDate(): Long {
        val trimmedDate = this.split(" ")

        if (trimmedDate[0] != "Há") return 0L

        val number = trimmedDate[1].toIntOrNull() ?: return 0L

        val unit = trimmedDate[2]

        val javaUnit = when (unit) {
            "ano", "anos" -> Calendar.YEAR
            "mês", "meses" -> Calendar.MONTH
            "semana", "semanas" -> Calendar.WEEK_OF_MONTH
            "dia", "dias" -> Calendar.DAY_OF_MONTH
            "hora", "horas" -> Calendar.HOUR
            "minuto", "minutos" -> Calendar.MINUTE
            "segundo", "segundos" -> Calendar.SECOND
            else -> return 0L
        }

        val now = Calendar.getInstance()

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    private fun generateRequestHeaders(): Headers {
        return headers.newBuilder().apply {
            csrfToken?.let { set("X-CSRF-Token", it) }
            set("X-Verification-Key-1", REQUEST_HEADER_KEY1)
            set("X-Verification-Key-2", REQUEST_HEADER_KEY2)
        }.build()
    }

    /**
     * Generates a header proof based on the provided challenge.
     * Equivalent to the JavaScript generateHeaderProof method.
     */
    private fun generateHeaderProof(challenge: String?, seed: Long): String? {
        if (challenge.isNullOrEmpty()) {
            return null
        }

        Log.d("SakuraMangas", "generating proof for challenge: $challenge")

        try {
            val decodedChallenge = String(Base64.decode(challenge, Base64.DEFAULT))
            Log.d("SakuraMangas", "decoded challenge: $decodedChallenge")
            val parts = decodedChallenge.split('/')

            if (parts.size != 3) {
                Log.e("SakuraMangas", "Invalid challenge format")
                return null
            }

            val ip = parts[0]
            val hash = parts[2]
            val userAgent = headers["User-Agent"]
            // Concatenate input similarly to the JS reference implementation
            val initial = (ip + userAgent + seed + hash)

            Log.d("SakuraMangas", "data for challenge: ip: '$ip', userAgent: '$userAgent'")

            // Iteratively hash the UTF-8 bytes of the previous output 29 times (SHA-256)
            // JS reference:
            // let out = proof;
            // for (let i = 0; i < 29; i++) {
            //   const bytes = encoder.encode(out);
            //   const digest = await crypto.subtle.digest('SHA-256', bytes);
            //   out = [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
            // }
            val digest = MessageDigest.getInstance("SHA-256")
            var out = initial
            repeat(29) {
                val bytes = out.toByteArray(Charsets.UTF_8)
                val hashed = digest.digest(bytes)
                // Convert to lowercase hex string
                out = hashed.joinToString("") { byte -> "%02x".format(byte) }
                digest.reset()
            }

            Log.d("SakuraMangas", "generated proof: $out")

            return out
        } catch (error: Exception) {
            Log.e("SakuraMangas", "Failed to generate header proof", error)
            return null
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = 15
        private const val CHAPTER_LIMIT = 90
        private const val REQUEST_HEADER_KEY1 = "a1b2c3d4-e5f6-7890-g1h2-i3j4k5l6m7n8"
        private const val REQUEST_HEADER_KEY2 = "z9y8x7w6-v5u4-3210-t9s8-r7q6p5o4n3m2"
        private const val PROOF_INFO_SEED = 8007199254740981
        private const val PROOF_CHAPTER_SEED = 9007199254740991
    }
}
