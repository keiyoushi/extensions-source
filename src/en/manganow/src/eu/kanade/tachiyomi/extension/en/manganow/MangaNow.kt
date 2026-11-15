package eu.kanade.tachiyomi.extension.en.manganow

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

@Source
abstract class MangaNow : MangaReader() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    // =============================== Pages ================================

    override fun pageListParseSelector() = ".container-reader-chapter > .iv-card:not([data-url$=manganow.jpg])"

    // =============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        Note,
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        ScoreFilter(),
        YearFilter(),
        getSortFilter(),
        GenreFilter(),
    )
}
