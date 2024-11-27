package eu.kanade.tachiyomi.extension.es.chochox

import eu.kanade.tachiyomi.multisrc.vercomics.VerComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class Chochox : VerComics("ChoChoX", "https://chochox.com", "es") {

    override val urlSuffix = "porno"
    override val genreSuffix = "tag"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url = baseUrl.toHttpUrl().newBuilder()
            url.addPathSegments("page")
            url.addPathSegments(page.toString())
            url.addQueryParameter("s", query)

            return GET(url.build(), headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is Genre -> {
                    if (filter.toUriPart().isNotEmpty()) {
                        url.addPathSegments(genreSuffix)
                        url.addPathSegments(filter.toUriPart())

                        url.addPathSegments("page")
                        url.addPathSegments(page.toString())
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = "div.search-page > div.entry"

    override var genres =
        arrayOf(
            Pair("Ver todos", ""),
            Pair("Anal", "anal-xxx-comics"),
            Pair("Comics Porno 3D", "comics-3d"),
            Pair("Culonas", "culonas-comicsporno-xxx"),
            Pair("Dragon Ball", "dragon-ball-porno"),
            Pair("Full Color", "full-color"),
            Pair("Furry Hentai", "furry-hentai-comics"),
            Pair("Futanari", "futanari-comics"),
            Pair("Hinata XXX", "hinata-xxx"),
            Pair("Lesbianas", "lesbianas"),
            Pair("Mamadas", "mamadas-comics-porno"),
            Pair("Milfs", "milfs-porno-comics"),
            Pair("My Hero Academia XXX", "my-hero-academia-xxx"),
            Pair("Naruto Hentai XXX", "naruto-hentai-xxx"),
            Pair("Parodia Porno", "parodia-porno"),
            Pair("Parodias Porno", "parodias-porno-comics-porno"),
            Pair("Series TV Porno", "series-tv-xxx-comics-porno"),
            Pair("Sonic", "sonic"),
            Pair("Steven Universe", "steven-universe-xxx"),
            Pair("Tetonas", "tetonas-comics"),
            Pair("Vaginal", "vaginal-comics-porno"),
        )
}
