package eu.kanade.tachiyomi.extension.all.eternalmangas

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp
import eu.kanade.tachiyomi.multisrc.mangaesp.SeriesDto
import eu.kanade.tachiyomi.multisrc.mangaesp.TopSeriesDto
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Response

open class EternalMangas(
    lang: String,
    private val internalLang: String,
) : MangaEsp(
    "EternalMangas",
    "https://eternalmangas.com",
    lang,
) {

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val responseData = json.decodeFromString<TopSeriesDto>(body)

        val topDaily = responseData.response.topDaily.flatten().map { it.data }
        val topWeekly = responseData.response.topWeekly.flatten().map { it.data }
        val topMonthly = responseData.response.topMonthly.flatten().map { it.data }

        val mangas = (topDaily + topWeekly + topMonthly).distinctBy { it.slug }
            .filter { it.language == internalLang }
            .map { it.toSManga(seriesPath) }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<LatestUpdatesDto>(response.body.string())
        val mangas = responseData.updates[internalLang]?.flatten()?.map { it.toSManga(seriesPath) } ?: emptyList()
        return MangasPage(mangas, false)
    }

    override fun searchMangaParse(response: Response, page: Int, query: String, filters: FilterList): MangasPage {
        val document = response.asJsoup()
        val script = document.select("script:containsData(self.__next_f.push)").joinToString { it.data() }
        val jsonString = MANGA_LIST_REGEX.find(script)?.groupValues?.get(1)
            ?: throw Exception(intl["comics_list_error"])
        val unescapedJson = jsonString.unescape()
        comicsList = json.decodeFromString<List<SeriesDto>>(unescapedJson)
            .filter { it.language == internalLang }
            .toMutableList()
        return parseComicsList(page, query, filters)
    }

    override fun pageListParse(response: Response): List<Page> {
        var document = response.asJsoup()

        document.selectFirst("body > form[method=post]")?.let {
            val action = it.attr("action")
            val inputs = it.select("input")

            val form = FormBody.Builder()
            inputs.forEach { input ->
                form.add(input.attr("name"), input.attr("value"))
            }

            document = client.newCall(POST(action, headers, form.build())).execute().asJsoup()
        }

        return document.select("main > img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    @Serializable
    class LatestUpdatesDto(
        val updates: Map<String, List<List<SeriesDto>>>,
    )
}
