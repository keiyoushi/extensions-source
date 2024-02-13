package eu.kanade.tachiyomi.multisrc.readerfront

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

abstract class ReaderFront(
    final override val name: String,
    final override val baseUrl: String,
    final override val lang: String,
) : HttpSource() {
    override val supportsLatest = true

    private val json by injectLazy<Json>()

    private val i18n = ReaderFrontI18N(lang)

    open val apiUrl = baseUrl.replaceFirst("://", "://api.")

    abstract fun getImageCDN(path: String, width: Int = 350): String

    override fun latestUpdatesRequest(page: Int) =
        GET("$apiUrl?query=${works(i18n.id, "updatedAt", "DESC", page, 12)}", headers)

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl?query=${works(i18n.id, "stub", "ASC", page, 120)}", headers)

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$apiUrl?query=${work(i18n.id, manga.url)}", headers)

    override fun chapterListRequest(manga: SManga) =
        GET("$apiUrl?query=${chaptersByWork(i18n.id, manga.url)}#${manga.url}", headers)

    override fun pageListRequest(chapter: SChapter): Request {
        val jsonObj = json.parseToJsonElement(chapter.url).jsonObject
        val id = jsonObj["id"]!!.jsonPrimitive.content
        return GET("$apiUrl?query=${chapterById(id.toInt())}", headers)
    }

    override fun latestUpdatesParse(response: Response) =
        response.parse<List<Work>>("works").map {
            SManga.create().apply {
                url = it.stub
                title = it.toString()
                thumbnail_url = getImageCDN(it.thumbnail_path)
            }
        }.let { MangasPage(it, false) }

    override fun popularMangaParse(response: Response) =
        latestUpdatesParse(response)

    override fun mangaDetailsParse(response: Response) =
        response.parse<Work>("work").let {
            SManga.create().apply {
                url = it.stub
                title = it.toString()
                thumbnail_url = getImageCDN(it.thumbnail_path)
                description = it.description
                author = it.authors!!.joinToString()
                artist = it.artists!!.joinToString()
                genre = buildString {
                    if (it.adult!!) append("18+, ")
                    append(it.demographic_name!!)
                    if (it.genres!!.isNotEmpty()) {
                        append(", ")
                        it.genres.joinTo(this, transform = i18n::get)
                    }
                    append(", ")
                    append(it.type!!)
                }
                status = when {
                    it.licensed!! -> SManga.LICENSED
                    it.status_name == "on_going" -> SManga.ONGOING
                    it.status_name == "completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                initialized = true
            }
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val stub = response.request.url.fragment ?: ""
        return response.parse<List<Release>>("chaptersByWork").map {
            SChapter.create().apply {
                val jsonObject = buildJsonObject {
                    put("id", it.id)
                    put("stub", stub)
                    put("volume", it.volume)
                    put("chapter", it.chapter)
                    put("subchapter", it.subchapter)
                }
                url = json.encodeToString(jsonObject)
                name = it.toString()
                chapter_number = it.number
                date_upload = it.timestamp
            }
        }
    }

    override fun pageListParse(response: Response) =
        response.parse<Chapter>("chapterById").let {
            it.mapIndexed { idx, page ->
                Page(idx, "", getImageCDN(it.path(page), page.width))
            }
        }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/work/$lang/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val jsonObj = json.parseToJsonElement(chapter.url).jsonObject
        val stub = jsonObj["stub"]!!.jsonPrimitive.content
        val volume = jsonObj["volume"]!!.jsonPrimitive.content
        val chpter = jsonObj["chapter"]!!.jsonPrimitive.content
        val subChpter = jsonObj["subchapter"]!!.jsonPrimitive.content
        return "$baseUrl/read/$stub/$lang/$volume/$chpter.$subChpter"
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        client.newCall(popularMangaRequest(page)).asObservableSuccess().map { res ->
            popularMangaParse(res).let { mp ->
                when {
                    query.isBlank() -> mp
                    !query.startsWith(STUB_QUERY) -> mp.filter {
                        it.title.contains(query, true)
                    }
                    else -> mp.filter {
                        it.url == query.substringAfter(STUB_QUERY)
                    }
                }
            }
        }!!

    private inline fun MangasPage.filter(predicate: (SManga) -> Boolean) =
        copy(mangas.filter(predicate))

    private inline fun <reified T> Response.parse(name: String) =
        json.parseToJsonElement(body.string()).jsonObject.run {
            if (containsKey("errors")) {
                throw Error(get("errors")!![0]["message"].content)
            }
            json.decodeFromJsonElement<T>(get("data")!![name])
        }

    private operator fun JsonElement.get(key: String) = jsonObject[key]!!

    private operator fun JsonElement.get(index: Int) = jsonArray[index]

    private inline val JsonElement.content get() = jsonPrimitive.content

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()
}
