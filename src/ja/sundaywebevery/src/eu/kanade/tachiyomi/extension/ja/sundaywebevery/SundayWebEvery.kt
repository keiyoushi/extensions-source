package eu.kanade.tachiyomi.extension.ja.sundaywebevery

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup

@Source
abstract class SundayWebEvery : GigaViewer() {
    override val seriesListIds = listOf(
        "3269754496454696025",
        "3269754496454696029",
        "3269754496454696030",
        "3269754496454696040",
        "3269754496454696045",
        "3269754496454696049",
        "3269754496454696050",
        "3269754496576310217",
        "3269754496454696059",
    )

    override suspend fun fetchCollection(ids: List<String>): List<SManga> {
        if (ids.isNotEmpty()) return super.fetchCollection(ids)

        return client.get("$baseUrl/series/yoru-sunday").asJsoup().select("ul.webry-series-list li a.webry-series-item-link").map {
            SManga.create().apply {
                title = it.selectFirst("h4.series-title")!!.text()
                thumbnail_url = it.selectFirst("div.thumb-wrapper img")?.absUrl("data-src")
                setSeriesUrl(it.absUrl("href"), thumbnail_url)
            }
        }
    }

    override fun getFilterOptions() = listOf(
        "連載作品" to seriesListIds,
        "読切" to listOf("3269754496400962241"),
        // The API leaves these series out of its lists
        "夜サンデー" to emptyList(),
    )
}
