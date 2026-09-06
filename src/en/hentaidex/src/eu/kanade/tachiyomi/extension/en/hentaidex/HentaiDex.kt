package eu.kanade.tachiyomi.extension.en.hentaidex

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement

@Source
abstract class HentaiDex : MangaThemesia() {

    override fun searchMangaUrl(page: Int, query: String) = super.searchMangaUrl(page, query).addQueryParameter("s", query)

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(Filter.Header("Text search ignores filters")) +
            super.getFilterList(data).list,
    )
}
