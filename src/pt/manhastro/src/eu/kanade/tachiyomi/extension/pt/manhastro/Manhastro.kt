package eu.kanade.tachiyomi.extension.pt.manhastro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import rx.Observable
import java.text.Normalizer

class Manhastro : HttpSource() {

    override val name: String = "Manhastro"

    override val baseUrl: String = "https://manhastro.net"

    override val client = network.cloudflareClient

    private val apiUrl: String = "https://api2.manhastro.net"

    override val lang: String = "pt-BR"

    private val database: Map<String, JsonElement> by lazy {
        client.newCall(GET("$apiUrl/dados"))
            .execute()
            .parseAs<JsonObject>()["data"]!!.jsonArray
            .associateBy { it.jsonObject["manga_id"]!!.jsonPrimitive.toString() }
    }

    override val supportsLatest: Boolean = true

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/rank/mensal", headers)

    private fun JsonElement.toSManga(): SManga {
        val entry = this.jsonObject
        return SManga.create().apply {
            title = entry["titulo"]!!.jsonPrimitive.content
            description = entry["descricao"]?.jsonPrimitive?.content
            thumbnail_url = entry["imagem"]?.let { "https://${it.jsonPrimitive.content}" }
            url = "/manga/${entry["manga_id"]!!.jsonPrimitive.content}"
            genre = entry["generos"]?.jsonPrimitive
                ?.content
                ?.parseAs<List<String>>()
                ?.joinToString()
            initialized = true
        }
    }
    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<ResponseWrapper<List<PopularMangaDto>>>().data
            .distinctBy(PopularMangaDto::id)
            .mapNotNull { dto ->
                database[dto.id.toString()]?.let { jsonElement -> jsonElement.toSManga() }
            }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/lancamentos", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.fromCallable {
            val mangas = database.values.filter { jsonElement ->
                val entry = jsonElement.jsonObject
                entry["titulo"]!!.jsonPrimitive.content.contains(query) ||
                    entry["titulo_brasil"]?.jsonPrimitive?.content?.contains(query) ?: false
            }.map { it.toSManga() }
            MangasPage(mangas, hasNextPage = false)
        }
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga {
        TODO("Not yet implemented")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        TODO("Not yet implemented")
    }

    override fun pageListParse(response: Response): List<Page> {
        TODO("Not yet implemented")
    }

    override fun imageUrlParse(response: Response): String {
        TODO("Not yet implemented")
    }

    // ============================== Utilities =================================

    private fun String.normalize(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(ACCENT_MARKS_REGEX, "")
    }

    private fun String.contains(other: String): Boolean {
        return normalize().contains(other.normalize(), ignoreCase = true)
    }

    companion object {
        val ACCENT_MARKS_REGEX = "\\p{M}+".toRegex()
    }
}
