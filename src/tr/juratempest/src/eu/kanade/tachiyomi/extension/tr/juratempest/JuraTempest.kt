package eu.kanade.tachiyomi.extension.tr.juratempest

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class JuraTempest : KeiSource() {

    // ================================================================
    // POPULAR
    // ================================================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val document = client.get("$baseUrl/").asJsoup()
        val mangas = document.select("div.swiper-slide").mapNotNull { slide ->
            val link = slide.selectFirst("a[href^=/explore/]") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = slide.selectFirst("div[class*=\"aspect-2/3\"] img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    // ================================================================
    // LATEST
    // ================================================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val document = client.get("$baseUrl/").asJsoup()
        val section = document.selectFirst("h2:containsOwn(Son Yüklenenler)")?.closest("section")
            ?: return MangasPage(emptyList(), false)

        val mangas = section.select("a[href^=/explore/]").mapNotNull { element ->
            val slug = element.attr("href").substringAfter("/explore/").substringBefore("/")
            if (slug.isEmpty()) return@mapNotNull null

            SManga.create().apply {
                url = "/explore/$slug"
                title = element.selectFirst("span.truncate.font-semibold")?.text() ?: return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    // ================================================================
    // SEARCH
    // ================================================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return MangasPage(emptyList(), false)

        val sitemapXml = client.get("$baseUrl/sitemap.xml").body.string()
        val slugs = sitemapEntryRegex.findAll(sitemapXml).map { it.groupValues[1] }.distinct().toList()

        val normalizedQuery = normalizeForSearch(query)
        val matchedSlugs = slugs.filter { normalizeForSearch(it).contains(normalizedQuery) }

        val pageSlugs = matchedSlugs.drop((page - 1) * SEARCH_PAGE_SIZE).take(SEARCH_PAGE_SIZE)
        val mangas = pageSlugs.map { slug ->
            val document = client.get("$baseUrl/explore/$slug").asJsoup()
            SManga.create().apply {
                url = "/explore/$slug"
                title = document.selectFirst("h1")?.text() ?: slug
                thumbnail_url = document.selectFirst("div[data-slot=manga-detail-hero-cover] img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, matchedSlugs.size > page * SEARCH_PAGE_SIZE)
    }

    private fun normalizeForSearch(text: String): String = text.lowercase()
        .replace("ç", "c")
        .replace("ş", "s")
        .replace("ğ", "g")
        .replace("ü", "u")
        .replace("ö", "o")
        .replace("ı", "i")
        .replace("i̇", "i")
        .replace(Regex("[^a-z0-9]+"), "")

    // ================================================================
    // MANGA DETAILS
    // ================================================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (segments.size != 2 || segments[0] != "explore") return null

        val manga = SManga.create().apply {
            this.url = "/explore/${segments[1]}"
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = manga.url
            }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val html = client.get(baseUrl + manga.url).body.string()
        val document = Jsoup.parse(html, baseUrl + manga.url)

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("div[data-slot=manga-detail-hero-cover] img")?.absUrl("src")
            description = document.selectFirst("p[data-slot=manga-detail-hero-description]")?.text()
            genre = document.select("div[data-slot=manga-detail-tags-genres] span[data-slot=badge]")
                .joinToString { it.text() }
            status = document.selectFirst("div[data-slot=manga-detail-metadata] span:has(svg.lucide-clock)")
                ?.text()
                ?.let(::parseStatus)
                ?: SManga.UNKNOWN
        }

        val hydratedChapters = chapterEntryRegex.findAll(html).map { match -> match.toChapter(manga.url) }.toList()

        val chapterList = hydratedChapters.ifEmpty {
            val visibleChapters = document.select("a[data-slot=chapter-row]").map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    name = element.selectFirst("span.truncate.font-medium")!!.text()
                    chapter_number = element.selectFirst("div.size-10")?.text()?.trim()?.toFloatOrNull() ?: -1f
                    date_upload = element.selectFirst("span.text-muted-foreground.text-xs")?.text()
                        ?.let { dateFormat.tryParseDate(it, istanbulZone) } ?: 0L
                }
            }
            visibleChapters + fillMissingChapters(manga.url, visibleChapters)
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun parseStatus(status: String): Int {
        val text = status.lowercase()
        return when {
            text.contains("devam") -> SManga.ONGOING
            text.contains("tamamlandı") -> SManga.COMPLETED
            text.contains("ara verildi") -> SManga.ON_HIATUS
            text.contains("iptal") || text.contains("bırakıldı") -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ================================================================
    // CHAPTERS
    // ================================================================

    private fun MatchResult.toChapter(mangaUrl: String): SChapter {
        val (slug, number, title, isSpecial, createdAt) = destructured
        return SChapter.create().apply {
            url = "$mangaUrl/$slug"
            name = title.replace("\\\"", "\"").replace("\\\\", "\\")
            chapter_number = number.toFloatOrNull() ?: -1f
            date_upload = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L)
            scanlator = if (isSpecial == "!0") "Özel" else null
        }
    }

    private suspend fun fillMissingChapters(mangaUrl: String, visibleChapters: List<SChapter>): List<SChapter> {
        val lowestWhole = visibleChapters
            .map { it.chapter_number }
            .filter { it > 0 && it == it.toInt().toFloat() }
            .minOrNull()
            ?.toInt()
            ?: return emptyList()

        if (lowestWhole <= 1) return emptyList()

        val discovered = mutableMapOf<Int, SChapter>()

        suspend fun probe(n: Int): Boolean {
            val response = try {
                client.get("$baseUrl$mangaUrl/$n")
            } catch (e: Exception) {
                return false
            }
            val ok = response.isSuccessful
            if (ok) {
                runCatching { response.body.string() }.getOrNull()?.let { body ->
                    chapterEntryRegex.findAll(body).forEach { match ->
                        val chapter = match.toChapter(mangaUrl)
                        val number = chapter.chapter_number
                        if (number > 0 && number == number.toInt().toFloat()) {
                            discovered[number.toInt()] = chapter
                        }
                    }
                }
            }
            response.close()
            return ok
        }

        var lastGood = lowestWhole
        var probeNumber = lowestWhole - 1
        var failedAt: Int? = null
        while (probeNumber >= 1) {
            if (probe(probeNumber)) {
                lastGood = probeNumber
                if (probeNumber == 1) break
                probeNumber = maxOf(1, probeNumber - 10)
            } else {
                failedAt = probeNumber
                break
            }
        }

        val lowerBound = failedAt?.let { badChapter ->
            var lo = badChapter + 1
            var hi = lastGood
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (probe(mid)) hi = mid else lo = mid + 1
            }
            lo
        } ?: 1

        if (lowerBound >= lowestWhole) return emptyList()

        return ((lowestWhole - 1) downTo lowerBound).map { n ->
            discovered[n] ?: SChapter.create().apply {
                url = "$mangaUrl/$n"
                name = "Bölüm $n"
                chapter_number = n.toFloat()
            }
        }
    }

    // ================================================================
    // PAGES
    // ================================================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        return document.select("div[data-slot=reader-images] img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    companion object {
        private const val SEARCH_PAGE_SIZE = 20

        private val istanbulZone = ZoneId.of("Europe/Istanbul")
        private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("tr"))

        private val sitemapEntryRegex = Regex("""<loc>[^<]*/explore/([a-z0-9-]+)</loc>""")

        private val chapterEntryRegex = Regex(
            """slug:"([^"]+)",number:([0-9.]+),title:"((?:[^"\\]|\\.)*)",isSpecial:(!0|!1),createdAt:(?:${'$'}\w+\[\d+]=)?new Date\("([^"]+)"\)""",
        )
    }
}
