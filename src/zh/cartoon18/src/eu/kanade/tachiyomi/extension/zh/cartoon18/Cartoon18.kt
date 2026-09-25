package eu.kanade.tachiyomi.extension.zh.cartoon18

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.net.URLDecoder

@Source
abstract class Cartoon18 :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val preferences by getPreferencesLazy()

    private val useTrad get() = preferences.getBoolean(PREF_ZH_HANT, false)

    private val baseUrlWithLang get() = if (useTrad) baseUrl else "$baseUrl/zh-hans"

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().resolve(manga.url)!!.toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)!!.toString()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val baseHost = baseUrl.toHttpUrl().host.removePrefix("www.")
        if (url.host.removePrefix("www.") != baseHost) return null
        val path = url.encodedPath
        if (!path.startsWith("/v/") && !path.startsWith("/zh-hans/v/")) return null
        val manga = SManga.create().apply {
            this.url = path
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrlWithLang?sort=hits&page=$page", headers)
        return mangaParse(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrlWithLang?sort=created&page=$page", headers)
        return mangaParse(response)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrlWithLang.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is KeywordFilter -> if (query.isBlank()) filter.addQueryTo(this)
                    is SortFilter -> filter.addQueryTo(this)
                    else -> {}
                }
            }
        }.build()

        val response = client.get(url, headers)
        return mangaParse(response)
    }

    private fun mangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#videos div.card").mapNotNull { card ->
            val link = card.selectFirst(".lines-2 a") ?: card.selectFirst("a.visited") ?: return@mapNotNull null
            val titleText = link.text().trim()
            if (titleText.isEmpty()) return@mapNotNull null

            val img = card.selectFirst(".embed-responsive img, img")
            SManga.create().apply {
                url = link.attr("href")
                title = titleText
                thumbnail_url = img?.let { el ->
                    el.attr("abs:data-src").ifEmpty { el.attr("abs:src") }
                }
                val genres = card.select(".card-body div a.badge")
                    .map { it.text().trim() }
                    .filter { it.isNotEmpty() }
                if (genres.isNotEmpty()) {
                    genre = genres.joinToString()
                }
            }
        }
        val isLastPage = document.selectFirst("nav .pagination .next").run {
            this == null || hasClass("disabled")
        }
        return MangasPage(mangas, !isLastPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), headers).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga {
        val titleText = document.selectFirst("div.content h1.title")?.ownText()?.trim().orEmpty()
        val authors = document.select("div.content h1.title ~ div.row div.my-2:has(i.fa-user) span")
        val authorText = if (authors.size > 1) authors[1].text().trim() else null
        val descs = document.select("div.content h1.title ~ div.row div.my-2:has(i.fa-list) span")
        val descText = if (descs.size > 1) descs[1].text().trim() else null
        val genres = document.select("div.content h1.title ~ div.row div.my-2:has(i.fa-tag) span:has(a) a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }

        return manga.apply {
            if (titleText.isNotEmpty()) {
                title = titleText
            } else if (title.isBlank()) {
                throw Exception("Missing manga title")
            }
            document.selectFirst("div.content h1.title ~ div.row a img")?.let { img ->
                thumbnail_url = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            }
            if (!authorText.isNullOrEmpty()) {
                author = authorText.replace(",(\\S)".toRegex(), ", $1")
            }
            if (!descText.isNullOrEmpty()) {
                description = descText
            }
            if (genres.isNotEmpty()) {
                genre = genres.joinToString()
            }
        }
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        val chapters = document.select("div.content h1.title + div a")
        return chapters.mapNotNull { el ->
            val nameText = el.text().trim()
            if (nameText.isEmpty()) return@mapNotNull null
            SChapter.create().apply {
                url = el.attr("href")
                name = nameText
            }
        }.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), headers).asJsoup()
        val images = document.select("div#app > div > a img, div#app img")
        return images.mapIndexed { index, image ->
            val url = image.attr("abs:src").ifEmpty { image.attr("abs:data-src") }
            Page(index, imageUrl = url)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrlWithLang/category", headers).asJsoup()
        val items = document.select("div.content a.btn")
        val keywords = items.mapNotNull { btn ->
            val href = btn.attr("href")
            val name = btn.text().trim()
            if (name.isEmpty() || href.isEmpty()) return@mapNotNull null
            val value = runCatching {
                URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
            }.getOrDefault(href.substringAfterLast('/'))
            KeywordDto(name, value)
        }
        return keywords.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(SortFilter())
        val keywords = data?.parseAs<List<KeywordDto>>().orEmpty()
        if (keywords.isNotEmpty()) {
            filters.add(KeywordFilter(listOf(KeywordDto("None", "")) + keywords))
        }
        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ZH_HANT
            title = "Use Traditional Chinese"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private open class QueryFilter(
        name: String,
        values: Array<String>,
        private val queryName: String,
        private val queryValues: Array<String>,
        state: Int = 0,
    ) : Filter.Select<String>(name, values, state) {
        fun addQueryTo(builder: HttpUrl.Builder) {
            val value = queryValues[state]
            if (value.isNotEmpty()) {
                builder.addQueryParameter(queryName, value)
            }
        }
    }

    private class SortFilter :
        QueryFilter(
            "Sort by",
            arrayOf("Latest", "Popular", "Recommended", "Best"),
            "sort",
            arrayOf("created", "hits", "score", "likes"),
            state = 2,
        )

    private class KeywordFilter(keywords: List<KeywordDto>) :
        QueryFilter(
            "Keyword",
            keywords.map { it.name }.toTypedArray(),
            "q",
            keywords.map { it.value }.toTypedArray(),
        )

    @Serializable
    private class KeywordDto(
        val name: String,
        val value: String,
    )

    companion object {
        private const val PREF_ZH_HANT = "ZH_HANT"
    }
}
