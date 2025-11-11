package eu.kanade.tachiyomi.extension.en.manganow

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class MangaNow : MangaReader(
    "MangaNow",
    "https://manganow.to",
    "en",
) {

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // =============================== Pages ================================

    override fun pageListParseSelector() = ".container-reader-chapter > .iv-card:not([data-url$=manganow.jpg])"

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
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
