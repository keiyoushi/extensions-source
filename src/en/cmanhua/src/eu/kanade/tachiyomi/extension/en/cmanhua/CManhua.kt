package eu.kanade.tachiyomi.extension.en.cmanhua

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
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CManhua : HttpSource() {
    override val name = "CManhua"
    override val baseUrl = "https://cmanhua.com"
    override val lang = "en"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("Referer") == null) {
                val newRequest = request.newBuilder()
                    .header("Referer", baseUrl)
                    .build()
                return@addInterceptor chain.proceed(newRequest)
            }
            chain.proceed(request)
        }
        .build()
    private val requestHeaders = headersBuilder().add("Referer", baseUrl).build()

    override fun popularMangaRequest(page: Int): Request = listRequest(page, sort = SORT_TOP_VIEWS)

    override fun latestUpdatesRequest(page: Int): Request = listRequest(page, sort = SORT_UPDATE_TIME)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return searchRequest(page, query.trim())
        }

        var status = STATUS_OPTIONS.first().second
        var sort = SORT_OPTIONS.first().second
        var minChapters = CHAPTER_OPTIONS.first().second
        var gender = GENDER_OPTIONS.first().second
        var genres = ""

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> status = filter.toUriPart()
                is SortFilter -> sort = filter.toUriPart()
                is ChapterCountFilter -> minChapters = filter.toUriPart()
                is GenderFilter -> gender = filter.toUriPart()
                is GenreFilter -> {
                    genres = filter.state.filter { it.state }.joinToString(",") { it.id }
                }
                else -> Unit
            }
        }

        return listRequest(
            page = page,
            sort = sort,
            status = status,
            minChapters = minChapters,
            gender = gender,
            genres = genres,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.title-detail")?.text()!!
            author = document.select("li.author p.col-xs-8 a").joinToString { it.text() }
            status = document.selectFirst("li.status p.col-xs-8")?.text().orEmpty().toStatus()
            genre = document.select("li.kind p.col-xs-8 a").joinToString { it.text() }
            description = document.selectFirst("#descript")?.text().orEmpty()
            thumbnail_url = document.selectFirst("div.col-image img")?.attr("src")
                ?.toAbsoluteUrl()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#listchap li.row").map { element ->
            val link = element.selectFirst("div.chapter a")!!
            val name = link.text()
            val date = element.selectFirst("time[datetime]")?.attr("datetime").orEmpty()

            SChapter.create().apply {
                this.name = name
                setUrlWithoutDomain(link.attr("href"))
                date_upload = dateFormat.tryParse(date)
                chapter_number = parseChapterNumber(name)
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val encoded = document.select("script")
            .asSequence()
            .map { it.data() }
            .mapNotNull { CHAPTER_TOKEN_REGEX.find(it)?.groupValues?.get(1) }
            .firstOrNull()
            ?: throw Exception("Unable to find chapter token")

        return fetchChapterPages(encoded, response.request.url.toString())
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used")
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, requestHeaders)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filters are ignored when searching by name."),
        SortFilter(),
        StatusFilter(),
        ChapterCountFilter(),
        GenderFilter(),
        GenreFilter(GENRES),
    )

    private fun listRequest(
        page: Int,
        sort: String,
        status: String = STATUS_OPTIONS.first().second,
        minChapters: String = CHAPTER_OPTIONS.first().second,
        gender: String = GENDER_OPTIONS.first().second,
        genres: String = "",
    ): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("danhsach/P$page/index.html")
            .addQueryParameter("status", status)
            .addQueryParameter("sort", sort)
            .addQueryParameter("chapter", minChapters)
            .addQueryParameter("gender", gender)
            .apply {
                if (genres.isNotBlank()) {
                    addQueryParameter("spec", genres)
                }
            }
            .build()

        return GET(url, headers)
    }

    private fun searchRequest(page: Int, query: String): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(query)
            .apply {
                if (page > 1) {
                    addPathSegment("P$page")
                }
            }
            .addPathSegment("tim-kiem.html")
            .build()

        return GET(url, headers)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("ul.lst_story li.item").map { element ->
            val titleElement = element.selectFirst("h3 a") ?: element.selectFirst("a[itemprop=url]")
            val url = titleElement?.attr("href")!!
            val title = titleElement.text()
            val thumbnail = element.selectFirst("img")?.let { image ->
                image.attr("data-src").ifBlank { image.attr("src") }
            }.orEmpty()

            SManga.create().apply {
                this.title = title
                thumbnail.toAbsoluteUrl().takeIf { it.isNotBlank() }?.let { thumbnailUrl ->
                    thumbnail_url = thumbnailUrl
                }
                setUrlWithoutDomain(url)
            }
        }

        val page = pageFromUrl(response.request.url)
        val hasNextPage = hasNextPage(document, page)
        return MangasPage(mangaList, hasNextPage)
    }

    private fun pageFromUrl(url: HttpUrl): Int {
        val pageSegment = url.pathSegments.firstOrNull { it.startsWith("P") && it.length > 1 }
        return pageSegment?.substring(1)?.toIntOrNull() ?: 1
    }

    private fun hasNextPage(document: Document, page: Int): Boolean {
        val maxPage = document.select("li.list-pager a")
            .mapNotNull { it.text().toIntOrNull() }
            .maxOrNull()
            ?: return false

        return page < maxPage
    }

    private fun fetchChapterPages(encoded: String, referer: String): List<Page> {
        val request = chapterApiRequest(encoded, referer)
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val data = extractChapterPayload(body)
            val code = data.trim().toIntOrNull()
            if (code != null) {
                throw Exception(errorMessage(code))
            }

            Jsoup.parseBodyFragment(data).select("img").mapIndexed { index, image ->
                Page(index, imageUrl = image.attr("src"))
            }
        }
    }

    private fun chapterApiRequest(encoded: String, referer: String): Request {
        val body = """{"enc":"$encoded"}""".toRequestBody(JSON_MEDIA_TYPE)
        return POST(
            "$baseUrl/Service.asmx/getchapter",
            headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .add("Referer", referer)
                .build(),
            body,
        )
    }

    private fun extractChapterPayload(body: String): String {
        val trimmed = body.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) {
            throw Exception("Failed to load chapter pages.")
        }
        if (trimmed.startsWith("<") || trimmed.contains("<img", ignoreCase = true)) {
            return trimmed
        }

        val jsonElement = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
            ?: throw Exception(errorMessageFromBody(trimmed))

        if (jsonElement !is JsonObject) {
            return jsonElement.jsonPrimitive.contentOrNull
                ?: throw Exception(errorMessageFromBody(trimmed))
        }

        val data = jsonElement["d"]?.jsonPrimitive?.contentOrNull
            ?: jsonElement["data"]?.jsonPrimitive?.contentOrNull
            ?: jsonElement["html"]?.jsonPrimitive?.contentOrNull
            ?: throw Exception(errorMessageFromBody(trimmed))

        return data
    }

    private fun errorMessageFromBody(body: String): String {
        val code = body.trim().toIntOrNull()
        if (code != null) {
            return errorMessage(code)
        }

        val jsonObject = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val error = jsonObject
            ?.get("error")
            ?.jsonPrimitive
            ?.contentOrNull

        return error ?: "Failed to load chapter pages."
    }

    private fun String.toAbsoluteUrl(): String {
        if (isBlank()) {
            return ""
        }
        return when {
            startsWith("http") -> this
            startsWith("//") -> "https:$this"
            else -> baseUrl + this
        }
    }

    private fun String.toStatus(): Int {
        return when (lowercase(Locale.ROOT)) {
            "on going", "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterNumber(name: String): Float {
        return CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: -1f
    }

    private open class UriPartFilter(
        displayName: String,
        private val options: Array<Pair<String, String>>,
    ) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
        fun toUriPart(): String = options[state].second
    }

    private class StatusFilter : UriPartFilter("Status", STATUS_OPTIONS)

    private class SortFilter : UriPartFilter("Order by", SORT_OPTIONS)

    private class ChapterCountFilter : UriPartFilter("Min chapters", CHAPTER_OPTIONS)

    private class GenderFilter : UriPartFilter("For", GENDER_OPTIONS)

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private fun errorMessage(code: Int): String {
        return when (code) {
            3 -> "You do not have enough coins to unlock this chapter."
            -1 -> "Chapter does not exist."
            -2 -> "Chapter token expired."
            -3 -> "Invalid parameters."
            -4 -> "Login required to access this chapter."
            -5 -> "Account is banned or does not exist."
            else -> "Failed to load chapter pages."
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val SORT_UPDATE_TIME = "0"
        private const val SORT_TOP_VIEWS = "2"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val CHAPTER_NUMBER_REGEX = Regex("""(?i)chapter\s*([0-9]+(?:\.[0-9]+)?)""")
        private val CHAPTER_TOKEN_REGEX = Regex("""var\s+ts\s*=\s*\"([^\"]+)\"""")

        private val SORT_OPTIONS = arrayOf(
            Pair("Update time", "0"),
            Pair("New Manhua", "1"),
            Pair("Top Views", "2"),
            Pair("Top Views Month", "3"),
            Pair("Top Views Week", "4"),
            Pair("Top Follow", "5"),
            Pair("Top Comment", "6"),
            Pair("Number Chapters", "7"),
        )

        private val STATUS_OPTIONS = arrayOf(
            Pair("All", "-1"),
            Pair("Ongoing", "0"),
            Pair("Completed", "1"),
        )

        private val CHAPTER_OPTIONS = arrayOf(
            Pair(">= 0 Chapter", "0"),
            Pair(">= 100 Chapter", "100"),
            Pair(">= 200 Chapter", "200"),
            Pair(">= 300 Chapter", "300"),
            Pair(">= 400 Chapter", "400"),
            Pair(">= 500 Chapter", "500"),
        )

        private val GENDER_OPTIONS = arrayOf(
            Pair("All", "-1"),
            Pair("Male", "0"),
            Pair("Female", "1"),
        )

        private val GENRES = listOf(
            Genre("ACTION", "68fed7700631ac1780da60fb"),
            Genre("ADAPTATION", "691b1a3d0631ac14cc79fc07"),
            Genre("ADVENTURE", "691c501b0631ac20d8f052ce"),
            Genre("Ability", "68fc7e9e0631ac1688c92be2"),
            Genre("Action", "66d02d130631ac1f64248dbb"),
            Genre("Adaptation", "66d055410631ac1f642a5c96"),
            Genre("Adenture", "691ffd2a0631ac2d8c9d058e"),
            Genre("Adult", "66d3439e0631ba299492391b"),
            Genre("Adventure", "66d02d130631ac1f64248dbc"),
            Genre("Aliens", "6926f15f0631ac0d7821ef71"),
            Genre("Animals", "66f115650631ac20947585fc"),
            Genre("Apocalypse", "66f10c130631ac209474b09b"),
            Genre("Beasts", "68e5cbc90631ac1e7c7f462f"),
            Genre("Blood", "68dcee320631ac17a8b9b3e4"),
            Genre("Bloody", "6926f15f0631ac0d7821ef70"),
            Genre("COMEDY", "691c501b0631ac20d8f052cd"),
            Genre("Cheat", "68dcecad0631ac17a8b8c98b"),
            Genre("Cheat Systems", "68fc3c870631ac347882afd5"),
            Genre("Childhood Friends", "671afb540631ac0f6c8610de"),
            Genre("Chinese", "670d0c0c0631ad2840edea67"),
            Genre("College life", "6713c7fc0631ac1cf8c79d6f"),
            Genre("Comedy", "66d02d130631ac1f64248dbd"),
            Genre("Comic", "66e17c8f0631ac324478c238"),
            Genre("Contest winning", "6926f15f0631ac0d7821ef72"),
            Genre("Cooking", "66d2c2270631ac299467be19"),
            Genre("Crime", "66d1e88c0631ac06a8bc9841"),
            Genre("Crossdressin", "66d786730631ac0df8365ac5"),
            Genre("Cultivation", "68e8c65b0631ac1560d49631"),
            Genre("DRAMA", "68fed7700631ac1780da60fd"),
            Genre("Delinquents", "66f597aa0631b00bd4815722"),
            Genre("Demons", "68e4bce40631ac10f4d353f6"),
            Genre("Dragon", "68e5d19c0631ac1e7c80d47a"),
            Genre("Drama", "66d01d560631ac1f642312ef"),
            Genre("Drama Sad Supernatural", "691be5dd0631ac20d8eec37b"),
            Genre("Dungeons", "672a9f720631ac2d644b0480"),
            Genre("Ecchi", "66d347ad0631bd29944d4b07"),
            Genre("Evolution", "691fe4200631ac2d8c9c67eb"),
            Genre("FANTASY", "68fed7700631ac1780da60fe"),
            Genre("FULL COLOR", "68fed7700631ac1780da60ff"),
            Genre("Fantasy", "66d01d570631ac1f642312f0"),
            Genre("Fantasy Harem", "691d8c080631ac031ce78715"),
            Genre("Fight", "68e61ac10631ac22a4390e84"),
            Genre("Fighting", "68dcea240631ac17a8b798c8"),
            Genre("Full Color", "66d2dba10631ac29946b9573"),
            Genre("Game", "66f0f9910631ac2094730f65"),
            Genre("Gender Bender", "66d300bf0631ac2994726b5e"),
            Genre("Ghost", "68e620e70631ac22a43e49c6"),
            Genre("Ghosts", "690df10e0631ac2af4ce1cb7"),
            Genre("Girl Power", "691d6e730631ac031ce6af9c"),
            Genre("Girls", "694208ef0631ac28ac180aec"),
            Genre("Gore", "66f0cfe90631ac20946f4ed9"),
            Genre("HAREM", "68f313eb0631ac1f5cfe3161"),
            Genre("HISTORICAL", "68f313eb0631ac1f5cfe3160"),
            Genre("Harem", "66d055410631ac1f642a5c97"),
            Genre("Historical", "66d01d570631ac1f642312f1"),
            Genre("Horror", "66d352c70631be2994fc6b5d"),
            Genre("Hunters", "692eafb00631ac19a045d178"),
            Genre("ISEKAI", "69115ee10631ac1de054fcc2"),
            Genre("Isekai", "66d01d570631ac1f642312f2"),
            Genre("Josei", "66d1cae50631ac06a8b89af7"),
            Genre("Josei(W)", "690d7ace0631ac2af4c62300"),
            Genre("Kids", "66e171f00631ac3244789626"),
            Genre("LONG STRIP", "68fed7700631ac1780da60fc"),
            Genre("Ladies", "68d6531c0631ac69e0c549b1"),
            Genre("Liexing", "66d1b5d70631ac06a8b48581"),
            Genre("Live action", "68e61adf0631ac22a4391b25"),
            Genre("Loli", "66e8743a0631ac3340b168f5"),
            Genre("Long Strip", "68fc3fa10631ac347883799b"),
            Genre("MAGIC", "691b1a3d0631ac14cc79fc04"),
            Genre("MARTIAL ARTS", "68f1ef020631ac2670dd00f2"),
            Genre("MONSTERS", "68f1ef020631ac2670dd00f0"),
            Genre("Magic", "66d16fb00631ac1f644c8c1e"),
            Genre("Magical", "66d40c370631be29940b6fc6"),
            Genre("Magical Girls", "692958510631ac1b744407e3"),
            Genre("Manga", "66dfabe60631ac146491b4dd"),
            Genre("Mangatoon", "66f0f6d90631ac209472c08d"),
            Genre("Manhua", "66d089940631ac1f642f2cd5"),
            Genre("Manhuaga Scans", "6945a1fc0631ac32244f9295"),
            Genre("Manhwa", "66d02d130631ac1f64248dbe"),
            Genre("Manhwa Hot", "67197bb10631ac14d04e2423"),
            Genre("Martial Arts", "66d06cc70631ac1f642c3e9f"),
            Genre("Martial arts", "690d89b30631ac2af4c713a4"),
            Genre("Mature", "66d3439e0631ba299492391c"),
            Genre("Mecha", "66d83fc20631ac0bfc9caeda"),
            Genre("Medical", "66d45ce40631ac2b18b73976"),
            Genre("Medicaldrama", "66f0f8670631ac209472dad7"),
            Genre("Military", "66f597aa0631b00bd4815723"),
            Genre("Moder", "66d343700631ba299492365e"),
            Genre("Monster", "68dde6bb0631ac28c0aa6d1d"),
            Genre("Monster Girls", "66f550680631b00bd477489b"),
            Genre("Monsters", "66d11bea0631ac1f643e1578"),
            Genre("Murim", "66dfd50d0631ac1464927f09"),
            Genre("Music", "66d786730631ac0df8365ac7"),
            Genre("Mystery", "66d1e4390631ac06a8bbfcd9"),
            Genre("Ngon Tinh", "6710c2c30631ac2b04a5fc1f"),
            Genre("Non-human", "692968910631ac1b7444383e"),
            Genre("OFFICIAL COLORED", "69115ee10631ac1de054fcc1"),
            Genre("OP MC", "692818140631ac1b743e4050"),
            Genre("OP-MC", "68dcecad0631ac17a8b8c98d"),
            Genre("Office Workers", "66e20c3f0631ac0cac257ad2"),
            Genre("Official Colored", "68fedf8e0631ac1780db980d"),
            Genre("Official colored", "6720683d0631ac1ac07fd3ec"),
            Genre("One shot", "66d2a0140631ac2994624947"),
            Genre("Op-Mc", "68dcee320631ac17a8b9b3e5"),
            Genre("Others", "68d641350631ac69e0b98d6e"),
            Genre("Overpowered", "68e4e24f0631ac10f4db444b"),
            Genre("Philosophical", "66d1e4390631ac06a8bbfcda"),
            Genre("Ping Ping Jun", "66d524a90631ac2b18c655fd"),
            Genre("Police", "66d786730631ac0df8365ac8"),
            Genre("Post Apocalyptic", "691ed70c0631ac2d8c9696d6"),
            Genre("Post apocalyptic", "66f707ac0631ac12247b3a79"),
            Genre("Post-Apocalyptic", "68fc84330631ac1688c97ff4"),
            Genre("Psychological", "66d0ab580631ac1f6432aeaa"),
            Genre("REINCARNATION", "68de2e5b0631ac28c0bcc010"),
            Genre("ROMANCE", "68e5cbe80631ac1e7c7f4fa9"),
            Genre("Rebirth", "6729e44a0631ac2d643df17b"),
            Genre("Regression", "690dfbf50631ac2af4cedd06"),
            Genre("Reincarnation", "66d139250631ac1f6440d6cc"),
            Genre("Revenge", "671372470631ac1cf8c17fab"),
            Genre("Reverse", "66d1b5d70631ac06a8b48582"),
            Genre("Reverse Harem", "690dae0e0631ac2af4c9644a"),
            Genre("Reverse harem", "66d1b5d70631ac06a8b48583"),
            Genre("Romance", "66d01d570631ac1f642312f3"),
            Genre("Royal family", "66d44d700631ac2b18b628a5"),
            Genre("Royalty", "6926a7db0631ac0d781f1731"),
            Genre("Ruthless Protagonist", "68dcee320631ac17a8b9b3e6"),
            Genre("SCHOOL LIFE", "691b1a3d0631ac14cc79fc05"),
            Genre("SEXUAL VIOLENCE", "66f0fbca0631ac2094738844"),
            Genre("SUGGESTIVE", "691b1a3d0631ac14cc79fc03"),
            Genre("SUPERHERO", "68de2e5b0631ac28c0bcc014"),
            Genre("SUPERNATURAL", "69115ee10631ac1de054fcc3"),
            Genre("School Life", "66d07c3a0631ac1f642d7c51"),
            Genre("School life", "6900066a0631ac1780e64970"),
            Genre("Sci-Fi", "691ed70c0631ac2d8c9696d7"),
            Genre("Sci-fi", "68e4d2980631ac10f4d71039"),
            Genre("Seinen", "66d1c77d0631ac06a8b7feb4"),
            Genre("Seinen(M)", "6926a79c0631ac0d781f1407"),
            Genre("Sexual Violence", "68fedf8e0631ac1780db980e"),
            Genre("Shoujo", "66d01d570631ac1f642312f4"),
            Genre("Shoujo Ai", "66d339170631ad29947c49ff"),
            Genre("Shoujo(G)", "690ddb800631ac2af4cccb66"),
            Genre("Shounen", "66d045090631ac1f64279346"),
            Genre("Shounen Ai", "66d3100a0631ac299475db10"),
            Genre("Shounen ai", "690e128d0631ac2af4cff7b5"),
            Genre("Shounen(B)", "690e128d0631ac2af4cff7b4"),
            Genre("Showbiz", "66fb3ed30631ac0f549f7dbf"),
            Genre("Si-fi", "670ab1fc0631ac3ec007f82b"),
            Genre("Slice of Life", "66d2afd30631ac2994648de9"),
            Genre("Slice of life", "68e5e6540631ac1e7c8cd621"),
            Genre("Smart MC", "68dcee320631ac17a8b9b3e7"),
            Genre("Smut", "66d343830631ba2994923827"),
            Genre("Soft Yaoi", "671b34c50631ac0f6c8a80e0"),
            Genre("Sports", "66d6a4e70631ac29b0ac9400"),
            Genre("Super Power", "68ddfcb90631ac28c0b01c88"),
            Genre("Super power", "691d83ca0631ac031ce741b3"),
            Genre("Superhero", "66d1e4390631ac06a8bbfcdb"),
            Genre("Supernatural", "66d045090631ac1f64279347"),
            Genre("Survival", "66d56e370631ac2b18cd6935"),
            Genre("System", "66f0f9910631ac2094730f66"),
            Genre("THRILLER", "68e5dc4e0631ac1e7c880ef6"),
            Genre("TIME TRAVEL", "6920a63b0631ac1e24c50cf8"),
            Genre("Tamer", "68dde6bb0631ac28c0aa6d1e"),
            Genre("Terror", "68e49b860631ac10f4cc2618"),
            Genre("Thriller", "66d315d80631ac299476fc45"),
            Genre("Time Travel", "66d11bea0631ac1f643e157a"),
            Genre("Time travel", "691d9f2e0631ac031ce80a7c"),
            Genre("Tragedy", "66d0d5ac0631ac1f64377cdc"),
            Genre("Transmigration", "66e171f00631ac3244789627"),
            Genre("Vampire", "66d3e7d60631be2994083f15"),
            Genre("Video Games", "66f115650631ac20947585fb"),
            Genre("Villainess", "66d2c2270631ac299467be1a"),
            Genre("Violence", "66e168f00631ac3244786bc7"),
            Genre("WEB COMIC", "691b1a3d0631ac14cc79fc06"),
            Genre("WUXIA", "68de02e90631ac28c0b1fcc2"),
            Genre("Weak-to-Strong", "68e4e60f0631ac10f4dc6a85"),
            Genre("Web Comic", "68de2c230631ac28c0bbcd4f"),
            Genre("Webtoon", "66d2d39f0631ac29946a43e3"),
            Genre("Webtoons", "68e5fb170631ac1e7c9e1496"),
            Genre("Wuxia", "66f0efa10631ac2094723e37"),
            Genre("Xianxia", "68de070e0631ac28c0b33e52"),
            Genre("Xuanhuan", "68e4d2980631ac10f4d7103a"),
            Genre("Yaoi", "68da064c0631ac14d06a8648"),
            Genre("Yaoi(BL)", "692a44ad0631ac31bc8fa9eb"),
            Genre("Yuri", "66ea188e0631ac2264610151"),
            Genre("Yuri(GL)", "69272aa90631ac0d7822957d"),
            Genre("Zombie", "691ec1920631ac2d8c96147a"),
            Genre("Zombies", "66f707ac0631ac12247b3a7a"),
            Genre("action", "691d8d4b0631ac031ce793b5"),
            Genre("adventure", "691db3ab0631ac031ce8cbe7"),
            Genre("apocalypse", "691f96040631ac2d8c99f6ab"),
            Genre("comedy", "691db1810631ac031ce8c489"),
            Genre("cooking", "691ed5790631ac2d8c968f76"),
            Genre("crime", "691ebd200631ac2d8c95e313"),
            Genre("ecchi", "68fc7cef0631ac1688c915ce"),
            Genre("fantasy", "691d9eec0631ac031ce8096d"),
            Genre("future era", "68dcea240631ac17a8b798c9"),
            Genre("gender bender", "68fc38b40631ac347881c145"),
            Genre("goddess", "68e624420631ac22a4414103"),
            Genre("harem", "691d8d4b0631ac031ce793b4"),
            Genre("horror", "691ebd200631ac2d8c95e312"),
            Genre("isekai", "691d8d4b0631ac031ce793b3"),
            Genre("ladies", "691dbbab0631ac031ce906c7"),
            Genre("mangatoon", "692811c20631ac1b743dfe17"),
            Genre("manhua", "68fc38b60631ac347881c148"),
            Genre("manhuaus", "691d8df40631ac031ce796e0"),
            Genre("manhwa", "692d12910631ac1404c26f9b"),
            Genre("martial arts", "691d8d4b0631ac031ce793b6"),
            Genre("mature", "6928570b0631ac1b74404879"),
            Genre("medical", "691ed02c0631ac2d8c967c8a"),
            Genre("romance", "691db1810631ac031ce8c488"),
            Genre("school life", "691db1340631ac031ce8c26b"),
            Genre("sci-fi", "691da4020631ac031ce82f49"),
            Genre("thriller", "691ec6700631ac2d8c963943"),
        )
    }
}
