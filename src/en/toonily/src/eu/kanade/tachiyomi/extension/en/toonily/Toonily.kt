package eu.kanade.tachiyomi.extension.en.toonily

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Toonily : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yy", Locale.US)

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("toonily-mature" to "1")
        addInterceptor(::hdCoverInterceptor)
    }

    override val mangaSubString = "serie"
    override val genreDirectory get() = "genre"
    override val filterNonMangaItems = false
    override val sendViewCount = false
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorDescription = "div.content-area div.summary__content"

    override fun searchCardSelector() = "div.page-item-detail.manga"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = super.getSearchMangaList(
        page,
        query.replace(titleSpecialCharactersRegex, " ").trim(),
        filters,
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = super.fetchMangaUpdate(
        manga.apply {
            url = url.replace("/webtoon/", "/$mangaSubString/")
        },
        chapters,
        fetchDetails,
        fetchChapters,
    )

    private fun hdCoverInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        return if (
            url.host.startsWith("static") && // covers are hosted on the static cdn, panels on data cdn
            url.pathSegments.lastOrNull()?.contains(sdCoverRegex) == true
        ) {
            try {
                val newUrl = url.newBuilder()
                    .removePathSegment(url.pathSegments.lastIndex)
                    .addPathSegment(
                        sdCoverRegex.replace(
                            url.pathSegments.last(),
                            "$1",
                        ),
                    ).build()
                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(newRequest)
                    .also { assert(it.isSuccessful) }
            } catch (_: Throwable) {
                chain.proceed(request)
            }
        } else {
            chain.proceed(request)
        }
    }

    companion object {
        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        val sdCoverRegex = Regex("""-[0-9]+x[0-9]+(\.\w+)$""")
    }
}
