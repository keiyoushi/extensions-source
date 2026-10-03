package eu.kanade.tachiyomi.multisrc.fansubscat

import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

abstract class FansubsCat : KeiSource() {

    protected open val apiBaseUrl: String
        get() = baseUrl.replace("https://manga.", "https://api.")

    abstract val isHentaiSite: Boolean

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("User-Agent", "Tachiyomi/${AppInfo.getVersionName()}")

    private fun parseMangaList(response: Response): MangasPage {
        val mangas = response.parseAs<ResultDto<List<MangaDto>>>().result.map { it.toSManga() }
        return MangasPage(mangas, mangas.size >= 20)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$apiBaseUrl/manga/popular/$page"))

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$apiBaseUrl/manga/recent/$page"))

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangaTypeFilter = filters.firstInstance<MangaTypeFilter>()
        val stateFilter = filters.firstInstance<StateFilter>()
        val genreFilter = filters.firstInstance<GenreTagFilter>()
        val themeFilter = filters.firstInstance<ThemeTagFilter>()
        val builder = "$apiBaseUrl/manga/search/$page".toHttpUrl().newBuilder()
        mangaTypeFilter.addQueryParameter(builder)
        stateFilter.addQueryParameter(builder)
        if (!isHentaiSite) {
            val demographyFilter = filters.firstInstance<DemographyFilter>()
            demographyFilter.addQueryParameter(builder)
        }
        genreFilter.addQueryParameter(builder)
        themeFilter.addQueryParameter(builder)
        if (query.isNotBlank()) {
            builder.addQueryParameter("query", query)
        }
        return parseMangaList(client.get(builder.build()))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.firstOrNull()?.takeIf(String::isNotBlank) ?: return null

        return fetchMangaDetails(slug)
    }

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.substringAfterLast('/')
        val details = async { if (fetchDetails) fetchMangaDetails(slug) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(slug) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    private suspend fun fetchMangaDetails(slug: String): SManga = client.get("$apiBaseUrl/manga/details/$slug").parseAs<ResultDto<MangaDto>>().result.toSManga()

    private suspend fun fetchChapterList(slug: String): List<SChapter> = client.get("$apiBaseUrl/manga/chapters/$slug")
        .parseAs<ResultDto<List<ChapterDto>>>()
        .result
        .map { it.toSChapter() }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiBaseUrl/manga/pages/${chapter.url.substringAfterLast('/')}")
        .parseAs<ResultDto<List<PageDto>>>()
        .result
        .mapIndexed { i, page -> page.toPage(i) }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url.replace("/", "?f=")}"

    // Filter
    override fun getFilterList(data: JsonElement?) = FilterList(
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
