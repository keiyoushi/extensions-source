package eu.kanade.tachiyomi.extension.ja.idolgravureprincessdate

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Source
abstract class IdolGravureprincessDate : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = client.get(apiUrlBuilder(page).build()).parseAs<BloggerDto>().toMangasPage()

    private fun BloggerDto.toMangasPage(): MangasPage {
        val manga = feed.entry.map { entry ->
            val content = Jsoup.parseBodyFragment(entry.content.t, baseUrl)

            SManga.create().apply {
                setUrlWithoutDomain(entry.link.first { it.rel == "alternate" }.href + "#${entry.published.t}")
                title = entry.title.t
                thumbnail_url = content.selectFirst("img")?.absUrl("src")
                genre = entry.category?.joinToString { it.term }
                status = SManga.COMPLETED
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                initialized = true
            }
        }
        val hasNextPage = feed.entry.size == MAX_RESULTS

        return MangasPage(manga, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchQuery = buildString {
            filters.filterIsInstance<LabelFilter>().forEach {
                it.state
                    .filter { f -> f.state }
                    .forEach { f ->
                        append(" label:\"")
                        append(f.name)
                        append("\"")
                    }
            }

            if (query.isNotEmpty()) {
                append(" ")
                append(query)
            }
        }.trim()
        val url = apiUrlBuilder(page)
            .addQueryParameter("q", searchQuery)
            .build()

        return client.get(url).parseAs<BloggerDto>().toMangasPage()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}".substringBefore("#")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val date = manga.url.substringAfter("#")

        val chapter = SChapter.create().apply {
            url = manga.url.substringBefore("#")
            name = "Gallery"
            date_upload = Instant.tryParse(date)
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("div.post-body a:has(> img)").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("href"))
        }
    }

    // filter name and list of values
    private val labelFilters = buildMap {
        put("Idol", getIdols())
        put("Magazines", getMagazine())
    }

    private fun getIdols() = listOf(
        "Nogizaka46",
        "AKB48",
        "NMB48",
        "Keyakizaka46",
        "HKT48",
        "SKE48",
        "NGT48",
        "SUPER☆GiRLS",
        "Morning Musume",
        "Dempagumi.inc",
        "Angerme",
        "Juice=Juice",
        "NijiCon-虹コン",
        "Houkago Princess",
        "Magical Punchline",
        "Idoling!!!",
        "Rev. from DVL",
        "Link STAR`s",
        "LADYBABY",
        "℃-ute",
        "Country Girls",
        "Up Up Girls (Kakko Kari)",
        "Yumemiru Adolescence",
        "Shiritsu Ebisu Chugaku",
        "Tenkoushoujo Kagekidan",
        "Drop",
        "Steam Girls",
        "Kamen Joshi's",
        "LinQ",
        "Doll☆Element",
        "TrySail",
        "Akihabara Backstage Pass",
        "Palet",
        "Passport☆",
        "Ange☆Reve",
        "BiSH",
        "Ciao Bella Cinquetti",
        "Gekidanherbest",
        "Haraeki Stage Ace",
        "Ru:Run",
        "SDN48",
    )

    private fun getMagazine() = listOf(
        "FLASH",
        "Weekly Playboy",
        "FRIDAY Magazine",
        "Young Jump",
        "Young Magazine",
        "BLT",
        "ENTAME",
        "EX-Taishu",
        "SPA! Magazine",
        "Young Gangan",
        "UTB",
        "Young Animal",
        "Young Champion",
        "Big Comic Spirtis",
        "Shonen Magazine",
        "BUBKA",
        "BOMB",
        "Shonen Champion",
        "Manga Action",
        "Weekly Shonen Sunday",
        "Photobooks",
        "BRODY",
        "Hustle Press",
        "ANAN Magazine",
        "SMART Magazine",
        "Young Sunday",
        "Gravure The Television",
        "CD&DL My Girl",
        "Daily LoGiRL",
        "Shukan Taishu",
        "Girls! Magazine",
        "Soccer Game King",
        "Weekly Georgia",
        "Sunday Magazine",
        "Mery Magazine",
    )

    class LabelFilter(name: String, labels: List<Label>) : Filter.Group<Label>(name, labels)

    class Label(name: String) : Filter.CheckBox(name)

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(apiUrlBuilder(1).build()).parseAs<BloggerDto>()
        .feed.category.map { it.term }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        labelFilters.forEach { (name, filter) ->
            filters.add(LabelFilter(name, filter.map(::Label)))
        }

        val categories = data?.parseAs<List<String>>()
        if (categories != null) {
            val existing = labelFilters.values.flatten()
            val others = categories
                .filterNot { existing.contains(it) }

            filters.add(LabelFilter("Other", others.map(::Label)))
        }

        return FilterList(filters)
    }

    private fun apiUrlBuilder(page: Int): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        // Blogger indices start from 1
        val startIndex = MAX_RESULTS * (page - 1) + 1

        addPathSegments("feeds/posts/default")
        addQueryParameter("alt", "json")
        addQueryParameter("max-results", MAX_RESULTS.toString())
        addQueryParameter("start-index", startIndex.toString())
    }

    companion object {
        private const val MAX_RESULTS = 25
    }
}
