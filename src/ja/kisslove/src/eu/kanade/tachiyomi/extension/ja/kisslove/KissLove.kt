package eu.kanade.tachiyomi.extension.ja.kisslove

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.util.Locale

@Source
abstract class KissLove : KeiSource() {
    override val supportsFilterFetching = true

    private val intl = Intl(
        Locale.getDefault().language,
        setOf("en", "ja", "zh"),
        lang,
        this::class.java.classLoader!!,
    )

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/trending-daily")
            .build()

        val response = client.get(url, sigAppend())
        val result = response.parseAs<List<Manga>>()
        val mangas = result.map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}.html"

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "36")
            .build()

        val response = client.get(url, sigAppend())
        val result = response.parseAs<PagedManga>()
        val mangas = result.items.map { it.toSManga() }
        val hasNextPage = result.currentPage < result.totalPages

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/list")
            addQueryParameter("search", query)
            addQueryParameter("sort", "Popular")
            addQueryParameter("order", "desc")

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val included = filter.state.filter { it.state }.joinToString(",") { it.name }
                        addQueryParameter("genre", included)
                    }

                    is StatusFilter -> addQueryParameter("status", filter.toUriPart())

                    else -> {}
                }
            }
        }.build()

        val response = client.get(url, sigAppend())
        val result = response.parseAs<ListPagedManga>()
        val mangas = result.items.map { it.toSManga() }
        val hasNextPage = result.currentPage < result.totalPages

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/slug")
            .addPathSegment(manga.url)
            .build()

        val response = client.get(url, sigAppend())
        val result = response.parseAs<Manga>()
        val manga = result.toSManga()
        val chapters = result.chapters
            .sortedByDescending { it.chapter }
            .map { it.toSChapter(manga.url) }

        return SMangaUpdate(manga, chapters)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterSuffix = chapter.url.substringAfterLast("/")

        return "$baseUrl/$chapterSuffix.html"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringBeforeLast("/")
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/chapter")
            .addPathSegment(id)
            .build()

        val response = client.get(url, sigAppend())
        val result = response.parseAs<Chapter>()

        return result.content
            .lines()
            .filter { it.isNotBlank() && !FILTER_IMG.contains(it) }
            .mapIndexed { i, img ->
                val url = img.toHttpUrl()
                val newHost = IMG_URL_MAPPING[url.host] ?: url.host

                Page(
                    index = i,
                    imageUrl = url.newBuilder().host(newHost).build().toString(),
                )
            }
    }

    private fun sigAppend(): Headers = headersBuilder().apply {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val payload = "$timestamp.$CLIENT_ID"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val signature = hashBytes.joinToString("") { byte ->
            "%02x".format(byte)
        }
        add("X-Client-Sig", signature)
        add("X-Client-Ts", timestamp)
    }.build()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterList = ArrayList<Filter<*>>()

        filterList.add(StatusFilter(intl["status"], statusList))

        val genres = data?.parseAs<List<Genre>>()?.map {
            CheckBoxFilter(it.name)
        }
        if (!genres.isNullOrEmpty()) {
            filterList.add(
                GenreFilter(
                    intl["genre"],
                    genres,
                ),
            )
        }

        return FilterList(filterList)
    }

    override suspend fun fetchFilterData(): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/genres")
            .build()

        val response = client.get(url, sigAppend())
        val genres = response.parseAs<List<Genre>>()

        return genres.toJsonElement()
    }

    private val statusList = arrayOf(
        intl["all"] to "",
        intl["ongoing"] to "Ongoing",
        intl["completed"] to "Completed",
    )

    private class StatusFilter(name: String, private val status: Array<Pair<String, String>>) : Filter.Select<String>(name, status.map { it.first }.toTypedArray()) {
        fun toUriPart() = status[state].second
    }

    private class GenreFilter(name: String, state: List<CheckBoxFilter>) : Filter.Group<CheckBoxFilter>(name, state)

    private class CheckBoxFilter(name: String) : Filter.CheckBox(name)

    companion object {
        private const val CLIENT_ID = "KL9K40zaSyC9K40vOMLLbEcepIFBhUKXwELqxlwTEF"
        private val FILTER_IMG = setOf(
            "https://1.bp.blogspot.com/-ZMyVQcnjYyE/W2cRdXQb15I/AAAAAAACDnk/8X1Hm7wmhz4hLvpIzTNBHQnhuKu05Qb0gCHMYCw/s0/LHScan.png",
            "https://s4.imfaclub.com/images/20190814/Credit_LHScan_5d52edc2409e7.jpg",
            "https://s4.imfaclub.com/images/20200112/5e1ad960d67b2_5e1ad962338c7.jpg",
        )
        private val IMG_URL_MAPPING = mapOf(
            "imfaclub.com" to "j1.jfimv2.xyz",
            "s2.imfaclub.com" to "j2.jfimv2.xyz",
            "s4.imfaclub.com" to "j4.jfimv2.xyz",
            "ihlv1.xyz" to "j1.jfimv2.xyz",
            "s2.ihlv1.xyz" to "j2.jfimv2.xyz",
            "s4.ihlv1.xyz" to "j4.jfimv2.xyz",
            "h1.klimv1.xyz" to "j1.jfimv2.xyz",
            "h2.klimv1.xyz" to "j2.jfimv2.xyz",
            "h4.klimv1.xyz" to "j4.jfimv2.xyz",
        )
    }
}
