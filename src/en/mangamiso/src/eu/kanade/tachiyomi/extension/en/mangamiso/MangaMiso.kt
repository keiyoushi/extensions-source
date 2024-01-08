package eu.kanade.tachiyomi.extension.en.mangamiso

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class MangaMiso : HttpSource() {

    companion object {
        const val MANGA_PER_PAGE = 20
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        const val PREFIX_ID_SEARCH = "id:"
    }

    override val name = "MangaMiso"

    override val baseUrl = "https://mangamiso.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val url = getBaseURLBuilder()
            .addPathSegment("mangas-get")
            .addPathSegment("get-new-mangas")
            .addQueryParameter("perPage", MANGA_PER_PAGE.toString())
            .addQueryParameter("page", page.toString())
            .toString()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaList = json.decodeFromString<MisoNewMangaPage>(response.body.string())
        val page = response.request.url.queryParameter("page")!!.toInt()
        val totalViewedManga = page * MANGA_PER_PAGE
        return MangasPage(mangaList.newManga.map(::toSManga), mangaList.total > totalViewedManga)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = getBaseURLBuilder()
            .addQueryParameter("perPage", MANGA_PER_PAGE.toString())
            .addQueryParameter("page", page.toString())

        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH)
                val url = "$baseUrl/mangas/$id"
                return GET(url, headers)
            }
            query.isNotBlank() -> throw UnsupportedOperationException("Text search currently not supported")
            else -> {
                val url = builder.addPathSegment("genres")

                var tagCount = 0
                filters.forEach { filter ->
                    when (filter) {
                        is MangaStatusFilter -> {
                            val statusSlug = filter.toUriPart()
                            url.addQueryParameter("status", statusSlug)
                        }
                        is UriPartFilter -> {
                            if (filter.toUriPart() != "") {
                                val genreSlug = filter.toUriPart()
                                url.addPathSegment(genreSlug)
                                tagCount++
                                if (tagCount > 1) {
                                    throw UnsupportedOperationException("Too many categories selected")
                                }
                            }
                        }
                        else -> {}
                    }
                }

                // If no filters selected, default to "all"
                if (tagCount == 0) { url.addPathSegment("all") }
                GET(url.toString(), headers)
            }
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        // This search was for a specific manga id
        return if (response.request.url.pathSegments[0] == "mangas") {
            val manga = mangaDetailsParse(response)
            MangasPage(listOf(manga), false)
        } else {
            val mangaList = json.decodeFromString<MisoBrowseManga>(response.body.string())
            val page = response.request.url.queryParameter("page")!!.toInt()
            val totalViewedManga = page * MANGA_PER_PAGE
            MangasPage(mangaList.foundList.map(::toSManga), mangaList.total > totalViewedManga)
        }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = getBaseURLBuilder()
            .addPathSegment("mangas-get")
            .addPathSegment("get-latestUpdate-mangas")
            .addQueryParameter("perPage", MANGA_PER_PAGE.toString())
            .addQueryParameter("page", page.toString())

        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangaList = json.decodeFromString<MisoLatestUpdatesPage>(response.body.string())
        val page = response.request.url.queryParameter("page")!!.toInt()
        val totalViewedManga = page * MANGA_PER_PAGE
        return MangasPage(mangaList.newManga.map(::toSManga), mangaList.total > totalViewedManga)
    }

    // Since mangaDetailsRequest is what drives the webview,
    // we are using this solely to provide that URL
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    // This is what actually gets the manga details
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val jsonURL = manga.url.replace("/manga/", "/mangas/")
        return client.newCall(
            GET(jsonURL, headers),
        ).asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response)
            }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaRoot = json.parseToJsonElement(response.body.string())
        val mangaObj = mangaRoot.jsonObject["manga"]!!

        return toSManga(json.decodeFromJsonElement(mangaObj))
    }

    private fun cleanDescription(mangaDesc: String): String {
        // Remove the link to the manga on other sites
        var description = "<p>Link:.*</p>".toRegex(RegexOption.IGNORE_CASE).replace(mangaDesc, "")

        // Convert any breaks <br> to newlines
        description = description.replace("<br>", "\n", true)

        // Convert any paragraphs to double newlines
        description = description.replace("<p>", "\n\n", true)

        // Replace any other tags with nothing
        description = "<.*?>".toRegex().replace(description, "")

        return description.trim()
    }

    private fun mapStatus(status: String) =
        when (status) {
            "ongoing", "hiatus" -> SManga.ONGOING
            "completed", "cancelled" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

    override fun chapterListRequest(manga: SManga): Request {
        val url = "${manga.url.replace("/manga/", "/mangas/")}/get-manga-chapters-12345?page=1&perPage=9999&sort=-1"
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterRoot = json.parseToJsonElement(response.body.string())

        val chapterBase = chapterRoot.jsonObject["chapters"]!!

        val chapterList = json.decodeFromJsonElement<MisoChapterList>(chapterBase)

        val path = response.request.url.pathSegments[1] // this is the pathName of the manga

        return chapterList.chapters.map { toSChapter(it, "$baseUrl/manga/$path") }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url.replace("/manga/", "/mangas/"), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterRoot = json.parseToJsonElement(response.body.string())

        val chapterBase = chapterRoot.jsonObject["chapter"]!!

        val pageList = json.decodeFromJsonElement<MisoPageList>(chapterBase)

        return pageList.pages.mapIndexed { index, misoPage ->
            val imgURL = "$baseUrl${misoPage.path}"
            Page(
                index,
                imgURL,
                imgURL,
            )
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    //region Filter Classes
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class MangaStatusFilter : UriPartFilter(
        "Manga Status",
        arrayOf(
            Pair("All", "all"),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Cancelled", "cancelled"),
            Pair("On Hiatus", "hiatus"),
        ),
    )

    private class DemographicFilter : UriPartFilter(
        "Demographic",
        arrayOf(
            Pair("<Select>", ""),
            Pair("Shounen", "shounen"),
            Pair("Shoujo", "shoujo"),
            Pair("Seinen", "seinen"),
            Pair("Josei", "josei"),
        ),
    )

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<Select>", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Crime", "crime"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Magical Girls", "magical_girls"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Mystery", "mystery"),
            Pair("Philosopical", "philosopical"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Shoujo Ai", "shoujo_ai"),
            Pair("Shounen Ai", "shounen_ai"),
            Pair("Slice of Life", "slice_of_life"),
            Pair("Sports", "sports"),
            Pair("Superhero", "superhero"),
            Pair("Thriller", "thriller"),
            Pair("Tragedy", "tragedy"),
            Pair("Wuxia", "wuxia"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ),
    )

    private class ThemeFilter : UriPartFilter(
        "Themes",
        arrayOf(
            Pair("<Select>", ""),
            Pair("Aliens", "aliens"),
            Pair("Animals", "animals"),
            Pair("Cooking", "cooking"),
            Pair("Crossdressing", "crossdressing"),
            Pair("Delinquents", "delinquents"),
            Pair("Demons", "demons"),
            Pair("Games", "games"),
            Pair("Gender bender", "gender_bender"),
            Pair("Ghosts", "ghosts"),
            Pair("Gyaru", "gyaru"),
            Pair("Harem", "harem"),
            Pair("Mafia", "mafia"),
            Pair("Magic", "magic"),
            Pair("Martial Arts", "martial_arts"),
            Pair("Military", "military"),
            Pair("Monster Girls", "monster_girls"),
            Pair("Monsters", "monsters"),
            Pair("Music", "music"),
            Pair("Ninja", "ninja"),
            Pair("Office", "office"),
            Pair("Police", "police"),
            Pair("Post Apocalyptic", "post_apocalyptic"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Reverse Harem", "reverse_harem"),
            Pair("Samurai", "samurai"),
            Pair("School Life", "school_life"),
            Pair("Supernatural", "supernatural"),
            Pair("Super Power", "super_power"),
            Pair("Survival", "survival"),
            Pair("Time Travel", "time_travel"),
            Pair("Vampires", "vampires"),
            Pair("Video Games", "video_games"),
            Pair("Villainess", "villainess"),
            Pair("Virtual Reality", "virtual_reality"),
            Pair("Zombies", "zombies"),
        ),
    )

    private class ContentTypeFilter : UriPartFilter(
        "Content Type",
        arrayOf(
            Pair("<Select>", ""),
            Pair("4-Koma", "4-Koma"),
            Pair("Anthology", "anthology"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Fan Colored", "fan-colored"),
            Pair("Full Colored", "full-colored"),
            Pair("Long Strip", "long_strip"),
            Pair("Manhwa", "manhwa"),
            Pair("Officially Colored", "officially-colored"),
            Pair("One Shot", "one_shot"),
            Pair("Partly-Colored", "partly-colored"),
            Pair("Web Comic", "web_comic"),
        ),
    )

    private class ContentWarningFilter : UriPartFilter(
        "Content Warning",
        arrayOf(
            Pair("<Select>", ""),
            Pair("Adult", "adult"),
            Pair("Ecchi", "ecchi"),
            Pair("Gore", "gore"),
            Pair("Sexual Violence", "sexual_violence"),
            Pair("Smut", "smut"),
        ),
    )
    private class GloryFilter : UriPartFilter(
        "Glory",
        arrayOf(
            Pair("<Select>", ""),
            Pair("Adaptation", "adaptation"),
            Pair("Adapted to Anime", "adapted_to_anime"),
            Pair("Award Winning", "award_winning"),
        ),
    )

    //endregion

    override fun getFilterList(): FilterList {
        return FilterList(
            MangaStatusFilter(),
            Filter.Header("Max 1 selection from any of the below categories"),
            DemographicFilter(),
            GenreFilter(),
            ThemeFilter(),
            ContentTypeFilter(),
            ContentWarningFilter(),
            GloryFilter(),
        )
    }

    private fun getBaseURLBuilder(): HttpUrl.Builder {
        return HttpUrl.Builder()
            .scheme("https")
            .host("mangamiso.net")
    }

    private fun toSManga(manga: MisoManga): SManga {
        return SManga.create().apply {
            title = manga.title.trim()
            author = manga.author.joinToString(",", transform = ::humanizeID)
            artist = manga.artist.joinToString(",", transform = ::humanizeID)
            thumbnail_url = "$baseUrl${manga.coverImage}"
            url = "$baseUrl/manga/${manga.pathName}"

            genre = manga.tags.joinToString(", ", transform = ::humanizeID)

            description = cleanDescription(manga.description)

            status = mapStatus(manga.status)
        }
    }

    private fun toSChapter(chapter: MisoChapter, mangaURL: String): SChapter {
        return SChapter.create().apply {
            name = chapter.title.trim()
            date_upload = try { DATE_FORMAT.parse(chapter.createdAt)!!.time } catch (e: Exception) { System.currentTimeMillis() }
            url = "$mangaURL/${chapter.pathName}"
            chapter_number = chapter.chapterNum
        }
    }

    // Convert the id of authors / artists / tags to a better form. (Eg. school_life -> School Life)
    private fun humanizeID(text: String) = text.split("_").joinToString(" ") { it.capitalize(Locale.US) }
}
