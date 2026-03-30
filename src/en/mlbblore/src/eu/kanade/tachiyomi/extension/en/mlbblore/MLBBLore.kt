package eu.kanade.tachiyomi.extension.en.mlbblore

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class MLBBLore : HttpSource() {

    override val name = "MLBB Lore Comics"
    override val baseUrl = "https://play.mobilelegends.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.mobilelegends.com"

    private val json: Json by injectLazy()

    private fun formRequest(url: String, params: Map<String, String>): Request {
        val formBody = FormBody.Builder()
        params.forEach { (key, value) -> formBody.add(key, value) }
        return POST(url, body = formBody.build())
    }

    override fun popularMangaRequest(page: Int): Request = formRequest(
        "$apiUrl/lore/album/list",
        mapOf(
            "type" to "3",
            "sort" to "3",
            "page" to page.toString(),
            "page_size" to "5",
            "lang" to "en",
            "token" to "",
        ),
    )

    override fun latestUpdatesRequest(page: Int): Request = formRequest(
        "$apiUrl/lore/album/list",
        mapOf(
            "type" to "3",
            "sort" to "1",
            "page" to page.toString(),
            "page_size" to "5",
            "lang" to "en",
            "token" to "",
        ),
    )

    private fun parseMangaListResponse(response: Response): MangasPage {
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        val data = root["data"]?.jsonArray ?: return MangasPage(emptyList(), false)

        val mangas = data.mapNotNull { element ->
            val obj = element.jsonObject

            // Ensure it's a comic (type 3)
            if (obj["type"]?.jsonPrimitive?.intOrNull != 3) return@mapNotNull null

            val id = obj["id"]?.jsonPrimitive?.intOrNull?.toString() ?: return@mapNotNull null

            SManga.create().apply {
                title = obj["title"]?.jsonPrimitive?.content.orEmpty()
                author = obj["hero_name"]?.jsonPrimitive?.content?.trim()
                thumbnail_url = obj["thumb"]?.jsonPrimitive?.content?.let {
                    if (it.startsWith("//")) "https:$it" else it
                }
                url = id
            }
        }

        return MangasPage(mangas, data.size >= 20)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListResponse(response)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListResponse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = formRequest(
        "$apiUrl/lore/album/detail",
        mapOf(
            "id" to manga.url,
            "lang" to "en",
            "token" to "",
        ),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        val data = root["data"]?.jsonObject ?: return SManga.create()

        return SManga.create().apply {
            title = data["title"]?.jsonPrimitive?.content.orEmpty()
            author = data["hero_name"]?.jsonPrimitive?.content?.trim()
            thumbnail_url = data["thumb"]?.jsonPrimitive?.content?.let {
                if (it.startsWith("//")) "https:$it" else it
            }
            description = data["share_content"]?.jsonPrimitive?.content.orEmpty()
            status = SManga.COMPLETED
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        val data = root["data"]?.jsonObject ?: return emptyList()
        val id = data["id"]?.jsonPrimitive?.intOrNull?.toString() ?: return emptyList()

        return listOf(
            SChapter.create().apply {
                name = "Chapter 1"
                chapter_number = 1f
                url = id
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request = formRequest(
        "$apiUrl/lore/album/detail",
        mapOf(
            "id" to chapter.url,
            "lang" to "en",
            "token" to "",
        ),
    )

    override fun pageListParse(response: Response): List<Page> {
        val root = json.parseToJsonElement(response.body.string()).jsonObject
        val data = root["data"]?.jsonObject ?: return emptyList()
        val images = data["comic_content"]?.jsonArray ?: return emptyList()

        return images.mapIndexedNotNull { index, element ->
            val raw = element.jsonPrimitive.contentOrNull ?: return@mapIndexedNotNull null
            val imageUrl = if (raw.startsWith("//")) "https:$raw" else raw
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListResponse(response)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
