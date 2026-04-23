package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://id.mgkomik.cc",
        "id",
        SimpleDateFormat("dd MMM yy", Locale.US),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = false

    override val filterNonMangaItems = false

    override val mangaSubString = "komik"

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Referer", "$baseUrl/")
        set("Sec-Fetch-Site", "none")
    }

    override fun popularMangaSelector() = "div.page-item-detail:not(:has(a[href*='bilibilicomics.com'])), .manga__item, .post-item, .manga-item"

    override val client = super.client.newBuilder()
        .rateLimit(9, 2)
        .build()

    // =========================== URL Migration ============================

    override fun getMangaUrl(manga: SManga): String {
        val url = manga.url.replace("mgkomik.com", "id.mgkomik.cc")
            .replace("/manga/", "/komik/")
        return if (url.startsWith("http")) url else "$baseUrl$url"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url.replace("mgkomik.com", "id.mgkomik.cc")
            .replace("/manga/", "/komik/")
        return if (url.startsWith("http")) url else "$baseUrl$url"
    }

    // ================================ Details ================================

    override val mangaDetailsSelectorDescription = "div.description-summary div.summary__content, div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt, .manga-description"

    // ================================ Chapters ================================

    override val chapterUrlSuffix = ""

    // =============================== Images ===============================

    override fun imageFromElement(element: Element): String? {
        val isPage = element.parents().any { it.hasClass("reading-content") || it.hasClass("page-break") }

        val url = if (isPage) {
            element.attr("abs:data-orig-file").ifEmpty {
                element.attr("abs:data-src").ifEmpty {
                    element.attr("abs:data-lazy-src").ifEmpty {
                        getBestSrcSetImage(element) ?: element.attr("abs:src")
                    }
                }
            }
        } else {
            // Use more stable extraction for covers as requested
            // Prefer data-src for covers too if it exists, but don't clean it
            element.attr("abs:data-src").ifEmpty {
                element.attr("abs:data-lazy-src").ifEmpty {
                    element.attr("abs:src")
                }
            }
        }

        val nonNullableUrl = url ?: ""
        return nonNullableUrl.takeIf { it.isNotBlank() }?.let {
            if (isPage) {
                var cleanUrl = it.replace(RESIZE_REGEX, "")
                    .replace(SCALED_REGEX, "$2")
                    .substringBefore("?resize=")
                    .substringBefore("?w=")

                // Bypass Photon if possible
                if (cleanUrl.contains("i0.wp.com") || cleanUrl.contains("i1.wp.com") || cleanUrl.contains("i2.wp.com")) {
                    cleanUrl = "https://" + cleanUrl.substringAfter("i0.wp.com/").substringAfter("i1.wp.com/").substringAfter("i2.wp.com/")
                }

                cleanUrl
            } else {
                it
            }
        }
    }

    private fun getBestSrcSetImage(element: Element): String? {
        val srcset = element.attr("srcset").takeIf { it.isNotBlank() } ?: return null
        return srcset.split(",")
            .mapNotNull {
                val parts = it.trim().split("\\s+".toRegex())
                if (parts.isEmpty()) return@mapNotNull null
                val url = element.absUrl(parts[0])
                if (url.isBlank()) return@mapNotNull null
                val width = parts.getOrNull(1)?.replace("w", "")?.toIntOrNull() ?: 0
                url to width
            }
            .maxByOrNull { it.second }
            ?.first
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, headers)
    }

    // ================================ Filters ================================

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = super.getFilterList().list.toMutableList()

        filters += if (genresList.isNotEmpty()) {
            listOf(
                Filter.Separator(),
                GenreContentFilter(
                    title = intl["genre_filter_title"],
                    options = genresList.map { it.name to it.id },
                ),
            )
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    private class GenreContentFilter(title: String, options: List<Pair<String, String>>) :
        UriPartFilter(
            title,
            options.toTypedArray(),
        )

    override fun genresRequest() = GET("$baseUrl/$mangaSubString", headers)

    override fun parseGenres(document: Document): List<Genre> {
        val genres = mutableListOf<Genre>()
        genres += Genre("All", "")
        genres += document.select(".row.genres li a, .checkbox-group .checkbox label").map { a ->
            Genre(a.text(), a.absUrl("href").ifEmpty { a.previousElementSibling()?.`val`() ?: "" })
        }
        return genres
    }

    // =============================== Utilities ==============================

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('.')
        return List(length) { charPool.random() }.joinToString("")
    }

    companion object {
        private val RESIZE_REGEX = "-\\d+x\\d+(?=\\.(jpg|jpeg|png|webp))".toRegex()
        private val SCALED_REGEX = "(-scaled)(\\.(jpg|jpeg|png|webp))".toRegex()
    }
}
