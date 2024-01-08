package eu.kanade.tachiyomi.multisrc.fansubscat

import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

abstract class FansubsCat(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val isHentaiSite: Boolean,
) : HttpSource() {

    private val apiBaseUrl = "https://api.fansubs.cat"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Tachiyomi/${AppInfo.getVersionName()}")

    override val client: OkHttpClient = network.client

    private val json: Json by injectLazy()

    private fun parseMangaFromJson(response: Response): MangasPage {
        val jsonObject = json.decodeFromString<JsonObject>(response.body.string())

        val mangas = jsonObject["result"]!!.jsonArray.map { json ->
            SManga.create().apply {
                url = json.jsonObject["slug"]!!.jsonPrimitive.content
                title = json.jsonObject["name"]!!.jsonPrimitive.content
                thumbnail_url = json.jsonObject["thumbnail_url"]!!.jsonPrimitive.content
                author = json.jsonObject["author"]!!.jsonPrimitive.contentOrNull
                description = json.jsonObject["synopsis"]!!.jsonPrimitive.contentOrNull
                status = json.jsonObject["status"]!!.jsonPrimitive.content.toStatus()
                genre = json.jsonObject["genres"]!!.jsonPrimitive.contentOrNull
            }
        }

        return MangasPage(mangas, mangas.size >= 20)
    }

    private fun parseChapterListFromJson(response: Response): List<SChapter> {
        val jsonObject = json.decodeFromString<JsonObject>(response.body.string())

        return jsonObject["result"]!!.jsonArray.map { json ->
            SChapter.create().apply {
                url = json.jsonObject["id"]!!.jsonPrimitive.content
                name = json.jsonObject["title"]!!.jsonPrimitive.content
                chapter_number = json.jsonObject["number"]!!.jsonPrimitive.float
                scanlator = json.jsonObject["fansub"]!!.jsonPrimitive.content
                date_upload = json.jsonObject["created"]!!.jsonPrimitive.long
            }
        }
    }

    private fun parsePageListFromJson(response: Response): List<Page> {
        val jsonObject = json.decodeFromString<JsonObject>(response.body.string())

        return jsonObject["result"]!!.jsonArray.mapIndexed { i, it ->
            Page(
                i,
                it.jsonObject["url"]!!.jsonPrimitive.content,
                it.jsonObject["url"]!!.jsonPrimitive.content,
            )
        }
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiBaseUrl/manga/popular/$page?hentai=$isHentaiSite", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiBaseUrl/manga/recent/$page?hentai=$isHentaiSite", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val mangaTypeFilter = filterList.find { it is MangaTypeFilter } as MangaTypeFilter
        val stateFilter = filterList.find { it is StateFilter } as StateFilter
        val demographyFilter = filterList.find { it is DemographyFilter } as DemographyFilter
        val genreFilter = filterList.find { it is GenreTagFilter } as GenreTagFilter
        val themeFilter = filterList.find { it is ThemeTagFilter } as ThemeTagFilter
        val builder = "$apiBaseUrl/manga/search/$page?hentai=$isHentaiSite".toHttpUrl().newBuilder()
        mangaTypeFilter.addQueryParameter(builder)
        stateFilter.addQueryParameter(builder)
        demographyFilter.addQueryParameter(builder)
        genreFilter.addQueryParameter(builder)
        themeFilter.addQueryParameter(builder)
        if (query.isNotBlank()) {
            builder.addQueryParameter("query", query)
        }
        return GET(builder.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(
            "$apiBaseUrl/manga/details/${manga.url.substringAfterLast('/')}?hentai=$isHentaiSite",
            headers,
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/${manga.url}"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = json.decodeFromString<JsonObject>(response.body.string())
        val resultObject = jsonObject.jsonObject["result"]!!.jsonObject

        return SManga.create().apply {
            url = resultObject["slug"]!!.jsonPrimitive.content
            title = resultObject["name"]!!.jsonPrimitive.content
            thumbnail_url = resultObject["thumbnail_url"]!!.jsonPrimitive.content
            author = resultObject["author"]!!.jsonPrimitive.contentOrNull
            description = resultObject["synopsis"]!!.jsonPrimitive.contentOrNull
            status = resultObject["status"]!!.jsonPrimitive.content.toStatus()
            genre = resultObject["genres"]!!.jsonPrimitive.contentOrNull
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("finished", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        return GET(
            "$apiBaseUrl/manga/chapters/${manga.url.substringAfterLast('/')}?hentai=$isHentaiSite",
            headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        parseChapterListFromJson(response)

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(
            "$apiBaseUrl/manga/pages/${chapter.url.substringAfterLast('/')}?hentai=$isHentaiSite",
            headers,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/${chapter.url.replace("/", "?f=")}"
    }

    override fun pageListParse(response: Response): List<Page> = parsePageListFromJson(response)

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

    // Filter
    override fun getFilterList() = FilterList(
        listOfNotNull(
            MangaTypeFilter("Tipus", getMangaTypeList()),
            StateFilter("Estat", getStateList()),
            if (!isHentaiSite) {
                DemographyFilter("Demografies", getDemographyList())
            } else {
                null
            },
            GenreTagFilter("Gèneres (inclou/exclou)", getGenreList()),
            ThemeTagFilter("Temàtiques (inclou/exclou)", getThemeList()),
        ),
    )

    private fun getMangaTypeList() = listOf(
        MangaType("oneshot", "One-shots"),
        MangaType("serialized", "Serialitzats"),
    )

    private fun getStateList() = listOf(
        State(1, "Completat"),
        State(2, "En procés"),
        State(3, "Parcialment completat"),
        State(4, "Abandonat"),
        State(5, "Cancel·lat"),
    )

    private fun getDemographyList() = listOf(
        Demography(35, "Infantil"),
        Demography(27, "Josei"),
        Demography(12, "Seinen"),
        Demography(16, "Shōjo"),
        Demography(1, "Shōnen"),
        Demography(-1, "No definida"),
    )

    private fun getGenreList() = listOfNotNull(
        Tag(4, "Acció"),
        Tag(7, "Amor"),
        Tag(38, "Amor entre noies"),
        Tag(23, "Amor entre nois"),
        Tag(31, "Avantguardisme"),
        Tag(6, "Aventura"),
        Tag(10, "Ciència-ficció"),
        Tag(2, "Comèdia"),
        Tag(47, "De prestigi"),
        Tag(3, "Drama"),
        Tag(19, "Ecchi"),
        Tag(46, "Erotisme"),
        Tag(20, "Esports"),
        Tag(5, "Fantasia"),
        Tag(48, "Gastronomia"),
        if (isHentaiSite) {
            Tag(34, "Hentai")
        } else {
            null
        },
        Tag(11, "Misteri"),
        Tag(8, "Sobrenatural"),
        Tag(17, "Suspens"),
        Tag(21, "Terror"),
        Tag(42, "Vida quotidiana"),
    )

    private fun getThemeList() = listOf(
        Tag(71, "Animals de companyia"),
        Tag(50, "Antropomorfisme"),
        Tag(70, "Arts escèniques"),
        Tag(18, "Arts marcials"),
        Tag(81, "Arts visuals"),
        Tag(64, "Canvi de gènere màgic"),
        Tag(56, "Comèdia de gags"),
        Tag(68, "Crim organitzat"),
        Tag(69, "Cultura otaku"),
        Tag(30, "Curses"),
        Tag(54, "Delinqüència"),
        Tag(43, "Detectivesc"),
        Tag(55, "Educatiu"),
        Tag(9, "Escolar"),
        Tag(39, "Espai"),
        Tag(77, "Esports d’equip"),
        Tag(53, "Esports de combat"),
        Tag(25, "Harem"),
        Tag(73, "Harem invers"),
        Tag(15, "Històric"),
        Tag(59, "Idols femenines"),
        Tag(60, "Idols masculins"),
        Tag(75, "Indústria de l’entreteniment"),
        Tag(61, "Isekai"),
        Tag(58, "Joc d’alt risc"),
        Tag(33, "Joc d’estratègia"),
        Tag(82, "Laboral"),
        Tag(29, "Mecha"),
        Tag(66, "Medicina"),
        Tag(67, "Memòries"),
        Tag(22, "Militar"),
        Tag(32, "Mitologia"),
        Tag(26, "Música"),
        Tag(65, "Noies màgiques"),
        Tag(36, "Paròdia"),
        Tag(49, "Personatges adults"),
        Tag(51, "Personatges bufons"),
        Tag(63, "Polígon amorós"),
        Tag(13, "Psicològic"),
        Tag(52, "Puericultura"),
        Tag(72, "Reencarnació"),
        Tag(62, "Relaxant"),
        Tag(74, "Rerefons romàntic"),
        Tag(37, "Samurais"),
        Tag(57, "Sang i fetge"),
        Tag(40, "Superpoders"),
        Tag(76, "Supervivència"),
        Tag(80, "Tirana"),
        Tag(45, "Transformisme"),
        Tag(41, "Vampirs"),
        Tag(78, "Viatges en el temps"),
        Tag(79, "Videojocs"),
    )

    private interface UrlQueryFilter {
        fun addQueryParameter(url: HttpUrl.Builder)
    }

    internal class MangaType(val id: String, name: String) : Filter.CheckBox(name)
    internal class State(val id: Int, name: String) : Filter.CheckBox(name)
    internal class Tag(val id: Int, name: String) : Filter.TriState(name)
    internal class Demography(val id: Int, name: String) : Filter.CheckBox(name)

    private class MangaTypeFilter(collection: String, mangaTypes: List<MangaType>) :
        Filter.Group<MangaType>(collection, mangaTypes),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder) {
            var oneShotSelected = false
            var serializedSelected = false
            state.forEach { mangaType ->
                if (mangaType.id.equals("oneshot") && mangaType.state) {
                    oneShotSelected = true
                } else if (mangaType.id.equals("serialized") && mangaType.state) {
                    serializedSelected = true
                }
            }
            if (oneShotSelected && !serializedSelected) {
                url.addQueryParameter("type", "oneshot")
            } else if (!oneShotSelected && serializedSelected) {
                url.addQueryParameter("type", "serialized")
            } else {
                url.addQueryParameter("type", "all")
            }
        }
    }

    private class StateFilter(collection: String, states: List<State>) :
        Filter.Group<State>(collection, states),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder) {
            state.forEach { state ->
                if (state.state) {
                    url.addQueryParameter("status[]", state.id.toString())
                }
            }
        }
    }

    private class DemographyFilter(collection: String, demographies: List<Demography>) :
        Filter.Group<Demography>(collection, demographies),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder) {
            state.forEach { demography ->
                if (demography.state) {
                    url.addQueryParameter("demographies[]", demography.id.toString())
                }
            }
        }
    }

    private class GenreTagFilter(collection: String, tags: List<Tag>) :
        Filter.Group<Tag>(collection, tags),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder) {
            state.forEach { tag ->
                if (tag.isIncluded()) {
                    url.addQueryParameter("genres_include[]", tag.id.toString())
                } else if (tag.isExcluded()) {
                    url.addQueryParameter("genres_exclude[]", tag.id.toString())
                }
            }
        }
    }

    private class ThemeTagFilter(collection: String, tags: List<Tag>) :
        Filter.Group<Tag>(collection, tags),
        UrlQueryFilter {

        override fun addQueryParameter(url: HttpUrl.Builder) {
            state.forEach { tag ->
                if (tag.isIncluded()) {
                    url.addQueryParameter("themes_include[]", tag.id.toString())
                } else if (tag.isExcluded()) {
                    url.addQueryParameter("themes_exclude[]", tag.id.toString())
                }
            }
        }
    }
}
