package eu.kanade.tachiyomi.extension.en.porncomix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response

class PornComix : Madara("PornComix", " https://porncomix.online", "en") {
    override val mangaSubString = "comic"
    override val useNewChapterEndpoint = true
    override val chapterUrlSuffix = ""
    override val fetchGenres = false
    override fun getFilterList() = FilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("action", "wp-manga-search-manga")
            add("title", query)
        }.build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = json.parseToJsonElement(response.body.string())

        val entries = data.jsonObject["data"]!!.jsonArray.filter {
            it.jsonObject["type"]!!.jsonPrimitive.content == "manga"
        }.map {
            val obj = it.jsonObject
            SManga.create().apply {
                title = obj["title"]!!.jsonPrimitive.content
                setUrlWithoutDomain(obj["url"]!!.jsonPrimitive.content)
            }
        }

        return MangasPage(entries, false)
    }
}
