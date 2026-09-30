package eu.kanade.tachiyomi.extension.en.hentairead

import android.util.Base64
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

@Source
abstract class Hentairead : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = mangasPage("$baseUrl/hentai/${searchPage(page)}?sortby=views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangasPage("$baseUrl/hentai/${searchPage(page)}?sortby=new")

    private fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    private suspend fun mangasPage(url: String): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select(".manga-item").map(::mangaFromElement)
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.manga-item__link")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.ownText()
        }
        thumbnail_url = element.selectFirst("img")?.let(::imageFromElement)
    }

    private fun imageFromElement(element: Element): String = when {
        element.hasAttr("data-src") -> element.absUrl("data-src")
        element.hasAttr("data-lazy-src") -> element.absUrl("data-lazy-src")
        element.hasAttr("srcset") -> element.absUrl("srcset").substringBefore(" ").removeSuffix(",")
        element.hasAttr("data-cfsrc") -> element.absUrl("data-cfsrc")
        else -> element.absUrl("src")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("page/$page")
            addQueryParameter("s", query)
            addQueryParameter("title-type", "contains")

            filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.forEach {
                addQueryParameter("categories[]", it.value)
            }

            filters.firstInstanceOrNull<PageFilter>()?.state?.takeIf(String::isNotBlank)?.let {
                val (min, max) = parsePageRange(it)
                addQueryParameter("pages", "$min-$max")
            }

            filters.firstInstanceOrNull<UploadedFilter>()?.state?.takeIf(String::isNotBlank)?.let {
                val type = when (it.firstOrNull()) {
                    '>' -> "after"
                    '<' -> "before"
                    else -> "in"
                }
                addQueryParameter("release-type", type)
                addQueryParameter("release", it.filter(Char::isDigit))
            }

            filters.filterIsInstance<TextFilter>().filter { it.state.isNotEmpty() }.forEach { filter ->
                filter.state.split(",").filter(String::isNotBlank).forEach { tag ->
                    val trimmed = tag.trim()
                    val name = trimmed.removePrefix("-")
                    val id = getTagId(name, filter.type)?.toString()
                        ?: throw Exception("${filter.type.lowercase().replaceFirstChar(Char::uppercase)} not found: $name")
                    if (filter.type == "manga_tag") {
                        addQueryParameter(if (trimmed.startsWith('-')) "excluding[]" else "including[]", id)
                    } else {
                        addQueryParameter("${filter.type}s[]", id)
                    }
                }
            }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("sortby", it.getValue())
                addQueryParameter("order", if (it.state!!.ascending) "asc" else "desc")
            }
        }.build()

        return mangasPage(url.toString())
    }

    private suspend fun getTagId(tag: String, type: String): Int? {
        val url = "$baseUrl/wp-admin/admin-ajax.php?action=search_manga_terms&search=$tag&taxonomy=$type".replace("artist", "manga_artist")
        return client.get(url).parseAs<Results>().results
            .firstOrNull { it.text.lowercase() == tag.lowercase() }
            ?.id
    }

    private fun parsePageRange(query: String, minPages: Int = 1, maxPages: Int = 9999): Pair<Int, Int> {
        val num = query.filter(Char::isDigit).toIntOrNull() ?: -1
        fun limitedNum(number: Int = num): Int = number.coerceIn(minPages, maxPages)

        if (num < 0) return minPages to maxPages
        return when (query.firstOrNull()) {
            '<' -> 1 to if (query[1] == '=') limitedNum() else limitedNum(num + 1)

            '>' -> limitedNum(if (query[1] == '=') num else num + 1) to maxPages

            '=' -> when (query[1]) {
                '>' -> limitedNum() to maxPages
                '<' -> 1 to limitedNum(maxPages)
                else -> limitedNum() to limitedNum()
            }

            else -> limitedNum() to limitedNum()
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) return null
        val mangaUrl = "$baseUrl/hentai/${url.pathSegments[1]}/"
        return mangaDetailsParse(client.get(mangaUrl).asJsoup()).apply {
            setUrlWithoutDomain(mangaUrl)
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) {
            mangaDetailsParse(client.get(getMangaUrl(manga)).asJsoup()).apply { url = manga.url }
        } else {
            manga
        }
        val chapter = SChapter.create().apply {
            name = "Chapter"
            url = manga.url
            if (details.description?.contains("Scanlators") == true) {
                scanlator = details.description?.substringAfter("Scanlators: ")?.substringBefore("\n")
            }
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    private fun mangaDetailsParse(document: Document): SManga {
        fun String.capitalizeEach() = this.split(" ").joinToString(" ") { s ->
            s.replaceFirstChar { sr ->
                if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
            }
        }
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.ownText()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            val authors = document.select("a[href*=/circle/] span:first-of-type").eachText().joinToString()
            val artists = document.select("a[href*=/artist/] span:first-of-type").eachText().joinToString()
            initialized = true
            author = authors.ifEmpty { artists }
            artist = artists.ifEmpty { authors }
            genre = document.select("a[href*=/tag/] span:first-of-type").eachText().joinToString()

            description = buildString {
                document.select("a[href*=/characters/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Characters: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/parody/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Parodies: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/circle/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Circles: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/convention/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Convention: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/scanlator/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Scanlators: ", it.capitalizeEach(), "\n\n")
                }
                document.selectFirst(".manga-titles h2")?.text()?.ifEmpty { null }?.let {
                    val titles = it.split("|").joinToString("\n") { "- ${it.trim()}" }
                    append("Alternative Titles: ", "\n", titles, "\n\n")
                }
                append(document.select(".items-center:contains(pages:)").text(), "\n")
            }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // chapterExtraData = ({...});
    private val chapterExtraDataRegex = Regex("""= (\{[^;]+)""")

    // window.mMjM5MjM2 = '(eyJkYX...);
    private val pagesDataRegex = Regex(""".(ey\S+).\s""")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // There's like 2 non-English entries where this breaks
        val url = "${chapter.url}english/p/1/".let { if (it.startsWith("http")) it else baseUrl + it }
        val document = client.get(url).asJsoup()

        val pageBaseUrl = document.selectFirst("[id=single-chapter-js-extra]")?.data()
            ?.let { chapterExtraDataRegex.find(it)?.groups }?.get(1)?.value
            ?.parseAs<ImageBaseUrlDto>()?.baseUrl

        val pages = document.selectFirst("[id=single-chapter-js-before]")?.data()
            ?.let { pagesDataRegex.find(it)?.groups }?.get(1)?.value
            ?.let { String(Base64.decode(it, Base64.DEFAULT)).parseAs<PagesDto>() }
            ?: throw Exception("Failed to find page list. Non-English entries are not supported.")

        return pages.data.chapter.images.mapIndexed { idx, page ->
            Page(idx, imageUrl = "$pageBaseUrl/${page.src}")
        }
    }
}
