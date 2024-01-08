package eu.kanade.tachiyomi.extension.ja.comicnewtype

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.select.Evaluator
import java.text.SimpleDateFormat
import java.util.Locale

class ComicNewtype : HttpSource() {
    override val name = "Comic Newtype"
    override val lang = "ja"
    override val baseUrl = "https://comic.webnewtype.com"
    override val supportsLatest = false

    // Latest is disabled because manga list is sorted by update time by default.
    // Ranking page has multiple rankings thus hard to do.

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/contents/?refind_search=all", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup().also { it.parseGenres() }
        if (document.selectFirst(Evaluator.Class("section__txt--serch")) != null) {
            return MangasPage(emptyList(), false)
        }

        val list = document.selectFirst(Evaluator.Class("content__col-list--common"))!!
        val mangas = list.children().map {
            val eyeCatcher = it.selectFirst(Evaluator.Class("catch__txt"))!!.ownText()
            val root = it.selectFirst(Evaluator.Tag("a"))!!
            SManga.create().apply {
                url = root.attr("href")
                title = root.selectFirst(Evaluator.Class("detail__txt--ttl"))!!.text()
                author = root.selectFirst(Evaluator.Class("detail__txt--info"))!!.ownText()
                thumbnail_url = baseUrl + root.selectFirst(Evaluator.Tag("img"))!!
                    .attr("src").removeSuffix("/w250/")
                val genreText = root.selectFirst(Evaluator.Class("detail__txt--label"))!!.ownText()
                if (genreText.isNotEmpty()) {
                    genre = genreText.substring(1).replace("#", ", ")
                }
                description = eyeCatcher
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used.")

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val path = when {
            query.isNotBlank() -> "/search/$query/"
            else -> filters.genrePath ?: "/contents/"
        }
        val url = baseUrl.toHttpUrl().newBuilder(path)!!.addQueries(filters).build()
        return Request.Builder().url(url).headers(headers).build()
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val root = response.asJsoup().selectFirst(Evaluator.Class("pc__list--contents"))!!
        title = root.selectFirst(Evaluator.Tag("h1"))!!.ownText()
        author = root.selectFirst(Evaluator.Class("contents__info"))!!.ownText()
        // This one is horizontal. Prefer the square one from manga list.
        // thumbnail_url = baseUrl + root.selectFirst(Evaluator.Class("contents__thumb-comic"))
        //     .child(0).attr("src").removeSuffix("/w500/")
        genre = root.selectFirst(Evaluator.Class("container__link-list--genre-btn"))
            ?.run { children().joinToString { it.text() } }

        val updates = root.selectFirst(Evaluator.Class("contents__date--info-comic"))!!
            .textNodes().filterNot { it.isBlank }.joinToString("  ||  ") { it.text() }
        val isCompleted = (updates == "連載終了")
        status = if (isCompleted) SManga.COMPLETED else SManga.ONGOING
        description = buildString {
            if (!isCompleted) append(updates).append("\n\n")
            append(root.selectFirst(Evaluator.Class("contents__txt-catch"))!!.ownText()).append("\n\n")
            append(root.selectFirst(Evaluator.Class("contents__txt--desc"))!!.ownText())
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "more/1/", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonObject = Json.parseToJsonElement(response.body.string()).jsonObject
        val html = jsonObject["html"]!!.jsonPrimitive.content // asserting ["next"] is 0
        return Jsoup.parseBodyFragment(html).body().children().mapNotNull { element ->
            val url = element.child(0).attr("href")
            if (url[0] != '/') return@mapNotNull null

            val dateEl = element.selectFirst(Evaluator.Class("detail__txt--date"))
            val title = element.selectFirst(Evaluator.Tag("h2"))!!.ownText().halfwidthDigits()
            val noteEl = element.selectFirst(Evaluator.Class("detail__txt--caution"))
            SChapter.create().apply {
                this.url = url
                name = if (noteEl == null) title else "$title（${noteEl.ownText()}）"
                dateEl?.let { dateFormat.parse(it.ownText()) }?.let { date_upload = it.time }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url + "json/", headers)

    override fun pageListParse(response: Response): List<Page> =
        Json.parseToJsonElement(response.body.string()).jsonArray.mapIndexed { index, jsonElement ->
            val path = when (jsonElement) {
                is JsonArray -> jsonElement[0]
                else -> jsonElement
            }.jsonPrimitive.content
            val newPath = path.removeSuffix("/h1200q75nc/")
            Page(index, imageUrl = baseUrl + newPath)
        }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun getFilterList() = filterList

    private val dateFormat by lazy { SimpleDateFormat("yyyy/M/d", Locale.ENGLISH) }
}
