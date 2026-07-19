package eu.kanade.tachiyomi.extension.all.onisaga

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class OniSaga :
    KeiSource(),
    ConfigurableSource {

    // Returns the uppercase language code required by the Livewire updates payload
    private val langCode: String? get() = when (lang) {
        "en" -> "EN"
        "fr" -> "FR"
        "ja" -> "JA"
        "pt-BR" -> "PT-BR"
        "pt" -> "PT"
        "es-419" -> "ES-LA"
        "es" -> "ES"
        else -> null
    }

    private val livewireJson = Json {
        encodeDefaults = true
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(4)
    }

    @Volatile private var cachedStateUrl: String? = null

    @Volatile private var cachedState: LivewireState? = null

    private val apiLock = Mutex()

    @Volatile private var lastRequestTime = 0L

    private val preferences: SharedPreferences = getPreferences()

    private fun buildLivewireHeaders(referer: String): Headers = headersBuilder()
        .set("X-Livewire", "")
        .set("Accept", "application/json")
        .set("X-Requested-With", "XMLHttpRequest")
        .set("Origin", baseUrl)
        .set("Referer", referer)
        .build()

    // =============================== Popular Manga ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val updates = PostFilterUpdatesDto(
            platform = typePref(),
            status = statusPref(),
            sort = "view",
        )
        return fetchMangaLivewirePage("$baseUrl/browse", page, updates)
    }

    // ================================ Latest Manga ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val updates = PostFilterUpdatesDto(
            platform = typePref(),
            status = statusPref(),
        )
        return fetchMangaLivewirePage("$baseUrl/browse", page, updates)
    }

    // ========================= Livewire Pagination Logic ==========================

    private suspend fun fetchMangaLivewirePage(
        url: String,
        page: Int,
        updates: PostFilterUpdatesDto? = null,
    ): MangasPage {
        val prefExcluded = excludedGenresPref()

        val state = if (cachedStateUrl == url && cachedState != null) {
            cachedState!!
        } else {
            val doc = client.get(url).asJsoup()

            if (page == 1 && updates == null && prefExcluded.isEmpty()) {
                return parseMangaList(doc)
            }

            val newState = doc.extractLivewireState("post-filter")
                ?: throw Exception("Could not find Livewire state for pagination")

            cachedStateUrl = url
            cachedState = newState
            newState
        }

        val effectiveUpdates = (updates ?: PostFilterUpdatesDto()).also { dto ->
            if (prefExcluded.isNotEmpty()) {
                dto.excludeGenre = (dto.excludeGenre + prefExcluded).distinct()
            }
        }

        val request = MangaLivewireRequest(
            token = state.token,
            components = listOf(
                MangaLivewireRequest.Component(
                    snapshot = state.snapshot,
                    updates = effectiveUpdates,
                    calls = listOf(
                        LivewireCall(
                            method = "gotoPage",
                            params = listOf(page.toString()),
                        ),
                    ),
                ),
            ),
        )

        val dto = client.post(
            "$baseUrl/livewire/update",
            buildLivewireHeaders(url.substringBefore("?")),
            request.toJsonRequestBody(livewireJson),
        ).parseAs<LivewireResponse>()

        dto.components.firstOrNull()?.snapshot?.let { newSnapshot ->
            cachedState = LivewireState(newSnapshot, state.token)
        }

        val html = dto.components.firstOrNull()?.effects?.html ?: ""

        return parseMangaList(Jsoup.parseBodyFragment(html, baseUrl))
    }

    private fun parseMangaList(doc: Document): MangasPage {
        val showNsfw = preferences.getBoolean(PREF_NSFW_KEY, false)
        val mangas = doc.select("div.relative.group").mapNotNull { it.toSManga(showNsfw) }

        val hasNextPage = doc.select("[wire:click*=nextPage]").any {
            !it.hasAttr("disabled")
        }

        return MangasPage(mangas, hasNextPage)
    }

    // =================================== Search ===================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment(query)
                .build()
                .toString()
        } else {
            "$baseUrl/browse"
        }
        val updates = buildUpdatesFromFilters(filters)
        return fetchMangaLivewirePage(url, page, updates)
    }

    private fun buildUpdatesFromFilters(filters: FilterList): PostFilterUpdatesDto? {
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val minChaptersFilter = filters.firstInstanceOrNull<MinChaptersFilter>()
        val groupFilter = filters.firstInstanceOrNull<GroupFilter>()
        val releaseStartFilter = filters.firstInstanceOrNull<ReleaseStartFilter>()
        val releaseEndFilter = filters.firstInstanceOrNull<ReleaseEndFilter>()

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val includeGenres = genreFilter?.state?.filter { it.state == Filter.TriState.STATE_INCLUDE }?.map { it.id }.orEmpty()
        val excludeGenres = genreFilter?.state?.filter { it.state == Filter.TriState.STATE_EXCLUDE }?.map { it.id }.orEmpty()

        val dto = PostFilterUpdatesDto(
            platform = typeFilter?.toUriPart() ?: "",
            status = statusFilter?.toUriPart() ?: "",
            sort = sortFilter?.toUriPart() ?: "created_at",
            minChapters = minChaptersFilter?.toUriPart() ?: "",
            group = groupFilter?.state?.trim()?.ifBlank { null },
            releaseStart = releaseStartFilter?.state?.trim()?.ifBlank { null },
            releaseEnd = releaseEndFilter?.state?.trim()?.ifBlank { null },
            genre = includeGenres,
            excludeGenre = excludeGenres,
        )

        return if (dto.platform.isEmpty() && dto.status.isEmpty() && dto.sort == "created_at" &&
            dto.minChapters.isEmpty() && dto.group == null && dto.releaseStart == null && dto.releaseEnd == null &&
            dto.genre.isEmpty() && dto.excludeGenre.isEmpty()
        ) {
            null
        } else {
            dto
        }
    }

    // ================================== Filters ===================================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        TypeFilter(),
        GenreFilter(genreList.map { GenreTriState(it.first, it.second) }),
        StatusFilter(),
        MinChaptersFilter(),
        GroupFilter(),
        ReleaseStartFilter(),
        ReleaseEndFilter(),
        SortFilter(),
    )

    // =============================== Manga Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga)
        val doc = client.get(url).asJsoup()

        val details = mangaDetailsParse(doc)
        val chapterList = if (fetchChapters) fetchChapterList(doc) else chapters

        return SMangaUpdate(details, chapterList)
    }

    private fun mangaDetailsParse(doc: Document): SManga {
        val nsfwSpan = doc.selectFirst("span:containsOwn(18+)")
        nsfwSpan?.closest("div.absolute.inset-0.z-20")?.remove()

        val badgeRow = doc.selectFirst("div.flex.items-center.gap-2.justify-center.mb-2")

        var bannerUrl = doc.selectFirst("img.absolute")?.let { resolveImageUrl(it) }

        val slug = doc.location().toHttpUrl().pathSegments.lastOrNull { it.isNotBlank() }

        return SManga.create().apply {
            title = doc.selectFirst("h1")?.text()
                ?: doc.selectFirst("[data-flux-heading]")?.text()
                ?: throw Exception("Could not find manga title")

            thumbnail_url = doc.selectFirst(".w-32 > picture:nth-child(1) > img:nth-child(3)")?.let { resolveImageUrl(it) }

            // If banner and cover are the same, do not display banner
            if (bannerUrl == thumbnail_url) {
                bannerUrl = null
            }

            memo = buildJsonObject {
                slug?.let { put("slug", it) }
            }

            val infoSection = doc.selectFirst("div.flex.flex-col.md\\:flex-row")

            author = infoSection?.select("a[href*=\"/author/\"]")?.joinToString { it.text() }

            genre = buildString {
                val types = badgeRow?.select("div[data-flux-badge]")?.mapNotNull { badge ->
                    val text = badge.text().lowercase()
                    if (text in listOf("manga", "manhwa", "manhua", "shounen", "seinen", "shoujo", "josei")) {
                        text.replaceFirstChar { it.uppercase() }
                    } else {
                        null
                    }
                } ?: emptyList()
                append(types.joinToString())

                val tags = infoSection?.select("a[href*=\"/genre/\"]")?.map { it.text() } ?: emptyList()
                if (tags.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(tags.joinToString())
                }
            }

            status = parseStatus(doc)

            description = buildString {
                val meta = mutableListOf<String>()

                badgeRow?.selectFirst("span:has(> span.size-1\\.5)")?.text()?.let { meta.add("**Status:** $it") }

                badgeRow?.select("span")?.firstOrNull { ORIGIN_REGEX.containsMatchIn(it.text()) }?.text()?.let { meta.add("**Origin:** $it") }

                badgeRow?.select("span")?.firstOrNull { it.text().matches(YEAR_REGEX) }?.text()?.let { meta.add("**Year:** $it") }

                doc.selectFirst("span.text-xs:matchesOwn(^\\d+\\.\\d+)")?.text()
                    ?.replace(RATING_REGEX, "$1")
                    ?.let { meta.add("**Rating:** $it") }

                val trackers = mutableListOf<String>()
                doc.selectFirst("a[href*=\"anilist.co\"]")?.attr("abs:href")?.let { trackers.add("[AniList]($it)") }
                doc.selectFirst("a[href*=\"myanimelist.net\"]")?.attr("abs:href")?.let { trackers.add("[MAL]($it)") }
                if (trackers.isNotEmpty()) meta.add("**Trackers:** ${trackers.joinToString(" | ")}")

                if (meta.isNotEmpty()) {
                    append(meta.joinToString(" | "))
                    append("\n\n---\n\n")
                }

                doc.selectFirst("p.leading-relaxed")?.text()?.let {
                    append(it)
                }

                doc.selectFirst("p[class*=\"text-[13px]\"]")?.text()?.let { altText ->
                    if (altText.isNotEmpty()) {
                        val altTitles = altText.split(INTERPUNCT_REGEX).filter { it.isNotEmpty() }
                        if (altTitles.isNotEmpty()) {
                            append("\n\n**Alternative Titles:**\n")
                            altTitles.forEach { t -> append("- $t\n") }
                        }
                    }
                }

                if (!bannerUrl.isNullOrBlank()) {
                    append("\n\n![Banner]($bannerUrl)")
                }
            }
        }
    }

    private fun parseStatus(doc: Document): Int {
        val statusText = (
            doc.selectFirst("span:has(> span.size-1\\.5)")?.text()
                ?: doc.selectFirst("span.inline-flex:matchesOwn(Completed|Ongoing|Hiatus|Cancelled)")?.text()
            )
            ?.lowercase() ?: return SManga.UNKNOWN

        return when {
            statusText.contains("ongoing") || statusText.contains("releasing") -> SManga.ONGOING
            statusText.contains("completed") -> SManga.COMPLETED
            statusText.contains("hiatus") -> SManga.ON_HIATUS
            statusText.contains("cancelled") || statusText.contains("dropped") -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun resolveImageUrl(img: Element): String? {
        val src = img.attr("data-src")
            .ifEmpty { img.attr("data-lazy-src") }
            .ifEmpty { img.attr("src") }
            .takeIf { it.isNotEmpty() && !it.startsWith("data:") }
            ?: return null

        return baseUrl.toHttpUrl().resolve(src)?.toString()
    }

    private fun Element.toSManga(showNsfw: Boolean): SManga? {
        val nsfwSpan = selectFirst("span:containsOwn(18+)")
        if (nsfwSpan != null && !showNsfw) return null
        nsfwSpan?.closest("div.absolute.inset-0.z-20")?.remove()

        val linkEl = if (tagName() == "a") this else selectFirst("a[href*=\"/manga/\"]")
        if (linkEl == null) return null

        val href = linkEl.absUrl("href")
        val httpUrl = href.toHttpUrlOrNull() ?: return null
        val pathSegments = httpUrl.pathSegments.filter { it.isNotBlank() }
        if (pathSegments.firstOrNull()?.equals("manga", true) != true || pathSegments.size < 2) return null

        val slug = pathSegments[1]

        val titleEl = selectFirst("div[data-flux-heading], h3, h4") ?: selectFirst("a[title]") ?: linkEl
        val currentTitle = titleEl.attr("title").ifEmpty { titleEl.text() }
        if (currentTitle.isEmpty()) return null

        val currentThumb = selectFirst("img[alt]:not([alt=''])")?.let { resolveImageUrl(it) }
            ?: selectFirst("img")?.let { resolveImageUrl(it) }

        return SManga.create().apply {
            title = currentTitle
            thumbnail_url = currentThumb
            setUrlWithoutDomain(href)
            memo = buildJsonObject {
                put("slug", slug)
            }
        }
    }

    // =============================== Related Manga ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val doc = client.get(baseUrl + manga.url).asJsoup()
        return relatedMangaListParse(doc)
    }

    private fun relatedMangaListParse(doc: Document): List<SManga> {
        val currentUrl = doc.location().toHttpUrl().encodedPath
        val showNsfw = preferences.getBoolean(PREF_NSFW_KEY, false)

        val heading = doc.select("div[data-flux-heading], h3, h2")
            .firstOrNull {
                val text = it.text().lowercase()
                text.contains("recommended") || text.contains("related") || text.contains("you may also like")
            } ?: return emptyList()

        val section = heading.parents().firstOrNull { it.select("div.relative.group, a[href*='/manga/']").size > 1 }
            ?: return emptyList()

        return section.select("div.relative.group, a[href*='/manga/']:has(img)")
            .mapNotNull { it.toSManga(showNsfw) }
            .filter { it.url != currentUrl }
            .distinctBy { it.url }
    }

    // ================================= Chapters ==================================

    private suspend fun fetchChapterList(doc: Document): List<SChapter> = coroutineScope {
        val nsfwSpan = doc.selectFirst("span:containsOwn(18+)")
        nsfwSpan?.closest("div.absolute.inset-0.z-20")?.remove()

        val state = doc.extractLivewireState("manga.chapter-list")
            ?: return@coroutineScope emptyList()

        // If langCode is null (the "all" source), loop through all 7 languages in parallel
        val langCodes = langCode?.let { listOf(it) }
            ?: listOf("EN", "FR", "JA", "PT-BR", "PT", "ES-LA", "ES")

        val deferredChapters = langCodes.map { code ->
            async {
                var currentSnapshot = state.snapshot
                var prevChapterCount = 0
                var langChapters = emptyList<SChapter>()

                while (true) {
                    val request = ChapterLivewireRequest(
                        token = state.token,
                        components = listOf(
                            ChapterLivewireRequest.Component(
                                snapshot = currentSnapshot,
                                updates = ChapterUpdatesDto(language = code),
                                calls = listOf(LivewireCall(method = "loadMoreChapters")),
                            ),
                        ),
                    )

                    val dto = client.post(
                        "$baseUrl/livewire/update",
                        buildLivewireHeaders(doc.location()),
                        request.toJsonRequestBody(livewireJson),
                    ).parseAs<LivewireResponse>()

                    val html = dto.components.firstOrNull()?.effects?.html ?: break
                    val chapterDoc = Jsoup.parseBodyFragment(html, baseUrl)

                    langChapters = parseChaptersFromDoc(chapterDoc, code, langCode == null)

                    if (langChapters.size <= prevChapterCount) break

                    prevChapterCount = langChapters.size
                    currentSnapshot = dto.components.firstOrNull()?.snapshot ?: break
                }
                langChapters
            }
        }

        deferredChapters.awaitAll().flatten().distinctBy { it.url }
            .sortedByDescending {
                CHAPTER_NUMBER_REGEX.find(it.name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            }
    }

    private fun parseChaptersFromDoc(doc: Document, langCode: String, isAllSource: Boolean): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // langCode is already uppercase ("EN", "FR", etc.)
        val langDisplay = langCode

        // Pattern 1: Direct chapter links (no dropdown)
        doc.select("a.gap-4:has(div[data-flux-heading])").forEach { el ->
            val headingText = el.selectFirst("div[data-flux-heading]")?.text()?.replace("Chapter ", "")
            val number = headingText?.ifBlank { null } ?: el.selectFirst("div.w-10")?.text() ?: return@forEach

            val url = el.absUrl("href").ifEmpty { el.attr("href") }
            if (url.isEmpty() || !url.contains("/read/")) return@forEach

            val textEl = el.selectFirst("p[data-flux-text]")
            val details = textEl?.text()?.replace(" - ", " · ")?.split(INTERPUNCT_REGEX)?.filter { it.isNotEmpty() } ?: emptyList()

            val dateStr = details.firstOrNull {
                val lower = it.lowercase()
                lower.contains("ago") || lower.contains("today") || lower.contains("yesterday")
            } ?: ""

            chapters.add(
                SChapter.create().apply {
                    name = "Chapter $number"
                    scanlator = if (isAllSource) langCode else null
                    date_upload = parseChapterDate(dateStr)
                    setUrlWithoutDomain(url)
                },
            )
        }

        // Pattern 2: Dropdowns (multiple versions/groups)
        doc.select("ui-dropdown:has(button div[data-flux-heading])").forEach { dropdown ->
            val button = dropdown.selectFirst("button") ?: return@forEach

            val headingText = button.selectFirst("div[data-flux-heading]")?.text()?.replace("Chapter ", "")
            val number = headingText?.ifBlank { null } ?: button.selectFirst("div.w-10")?.text() ?: return@forEach

            val textEl = button.selectFirst("p[data-flux-text]")
            val details = textEl?.text()?.replace(" - ", " · ")?.split(INTERPUNCT_REGEX)?.filter { it.isNotEmpty() } ?: emptyList()
            val dateStr = details.firstOrNull {
                val lower = it.lowercase()
                lower.contains("ago") || lower.contains("today") || lower.contains("yesterday")
            } ?: ""

            val links = dropdown.select("ui-menu a[data-flux-menu-item]")
            var unknownCount = 1

            links.forEach { linkEl ->
                val url = linkEl.absUrl("href").ifEmpty { linkEl.attr("href") }
                if (url.isEmpty() || !url.contains("/read/")) return@forEach

                // Extract group name from the span inside the menu item
                val group = linkEl.selectFirst("span.text-sm")?.text()
                    ?: linkEl.selectFirst("div.flex.items-center.gap-2 > span:not(.ml-auto)")?.text()
                    ?: ""

                // If group is missing or "Unknown group", name them Unknown 1, Unknown 2...
                val groupName = when {
                    group.isBlank() || group.equals("Unknown group", true) -> "Unknown $unknownCount"
                    else -> group
                }
                if (group.isBlank() || group.equals("Unknown group", true)) unknownCount++

                chapters.add(
                    SChapter.create().apply {
                        name = "Chapter $number"
                        // Append language to scanlator if it's the "all" source
                        scanlator = if (isAllSource) "$langDisplay - $groupName" else groupName
                        date_upload = parseChapterDate(dateStr)
                        setUrlWithoutDomain(url)
                    },
                )
            }
        }

        return chapters
    }

    private fun parseChapterDate(dateStr: String): Long {
        val date = dateStr.lowercase()
        if (date.isEmpty()) return 0L

        val now = System.currentTimeMillis()

        if (date.contains("today")) return now
        if (date.contains("yesterday")) return now - 86_400_000L

        val match = RELATIVE_DATE_REGEX.find(date) ?: return 0L

        val value = match.groupValues[1].toInt()
        val unit = match.groupValues[2]

        return when (unit) {
            "minute" -> now - (value * 60_000L)
            "hour" -> now - (value * 3_600_000L)
            "day" -> now - (value * 86_400_000L)
            "week" -> now - (value * 604_800_000L)
            "month" -> now - (value * 2_592_000_000L)
            "year" -> now - (value * 31_536_000_000L)
            else -> 0L
        }
    }

    // ============================== Pages & Images ===============================

    @Volatile
    private var currentReaderToken: String = ""

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = baseUrl + chapter.url
        val body = client.get(chapterUrl).body.string()

        val token = READER_TOKEN_REGEX.find(body)?.groupValues?.get(1) ?: ""
        if (token.isBlank()) throw Exception("Could not find readerToken in chapter page")

        currentReaderToken = token

        val pageCount = PAGE_ORDER_REGEX.findAll(body).count()

        return (0 until pageCount).map { index ->
            Page(index, url = "$chapterUrl#$index")
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val chapterUrl = page.url.substringBefore("#")
        val cid = chapterUrl.toHttpUrl().pathSegments.last()
        val order = page.index
        val apiUrl = "$baseUrl/api/chapter/$cid/page/$order"

        var token = currentReaderToken
        var attempt = 0

        while (attempt < 3) {
            attempt++

            apiLock.withLock {
                val rateLimitDelay = preferences.getString(PREF_RATE_LIMIT_KEY, "2000")?.toLongOrNull() ?: 2000L
                val now = System.currentTimeMillis()
                val waitTime = (rateLimitDelay - (now - lastRequestTime)).coerceAtLeast(0L)
                // Using rateLimit from keiyoushi.network.rateLimit was too aggressive, leading to 429 errors when it was fine after the rest of the images loaded
                // Note: Error 429 lasts for approximately 15-30 minutes. Had to jump between many VPN servers to reach this conclusion
                if (waitTime > 0) delay(waitTime)
                lastRequestTime = System.currentTimeMillis()
            }

            val response = client.get(apiUrl, apiHeaders(token, chapterUrl), ensureSuccess = false)

            // Handle 429 just in case (still holding the lock)
            if (response.code == 429) {
                val rateLimitDelay = preferences.getString(PREF_RATE_LIMIT_KEY, "2000")?.toLongOrNull() ?: 2000L
                val retryAfter = response.header("retry-after")?.toLongOrNull()?.times(1000L) ?: rateLimitDelay
                response.close()
                apiLock.withLock {
                    delay(retryAfter)
                    lastRequestTime = System.currentTimeMillis()
                }
                continue
            }

            // Update token globally if the server provides a new one
            response.header("x-reader-token-next")?.takeIf { it.isNotBlank() }?.let {
                currentReaderToken = it
            }

            val dto = response.parseAs<PageApiResponse>()

            if (dto.url != null) {
                return dto.url
            }

            if (!response.isSuccessful || dto.message?.contains("expired", ignoreCase = true) == true) {
                val refreshBody = client.get(chapterUrl).body.string()
                val newToken = READER_TOKEN_REGEX.find(refreshBody)?.groupValues?.get(1)

                if (newToken.isNullOrBlank()) {
                    throw Exception("Failed to refresh reader token (HTTP ${response.code})")
                }

                token = newToken
                currentReaderToken = newToken
                continue
            }

            throw Exception("API Error: ${dto.message}")
        }

        throw Exception("Failed to fetch image after 3 retries.")
    }

    override fun imageRequest(page: Page): Request {
        val referer = page.url.substringBeforeLast("#")
        return GET(page.imageUrl!!, headersBuilder().set("Referer", referer).build())
    }

    // ================================== Helpers ===================================

    private fun apiHeaders(token: String, referer: String) = headersBuilder()
        .set("X-Reader-Token", token)
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Referer", referer)
        .build()

    private fun Document.extractLivewireState(componentName: String): LivewireState? {
        val token = selectFirst("meta[name=csrf-token]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: selectFirst("input[name=_token]")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: return null

        for (el in select("*")) {
            val snapshotAttr = el.attributes().firstOrNull { it.key.endsWith("snapshot") }
            if (snapshotAttr != null) {
                val snapshot = snapshotAttr.value
                if (snapshot.contains(componentName)) {
                    return LivewireState(snapshot = snapshot, token = token)
                }
            }
        }
        return null
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]?.string ?: manga.url.substringAfterLast("/")
        return "$baseUrl/manga/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val pathSegments = url.pathSegments.filter { it.isNotBlank() }
        val mangaUrl = when {
            pathSegments.firstOrNull()?.equals("manga", true) == true && pathSegments.size >= 2 -> {
                "${url.scheme}://${url.host}/manga/${pathSegments[1]}"
            }
            pathSegments.firstOrNull()?.equals("read", true) == true -> {
                val doc = client.get(url.toString()).asJsoup()
                doc.selectFirst("a[href*=\"/manga/\"]")?.absUrl("href") ?: return null
            }
            else -> return null
        }

        val doc = client.get(mangaUrl).asJsoup()
        return mangaDetailsParse(doc).apply {
            setUrlWithoutDomain(mangaUrl)
        }
    }

    class LivewireState(val snapshot: String, val token: String)

    private fun excludedGenresPref(): Set<String> {
        val showNsfw = preferences.getBoolean(PREF_NSFW_KEY, false)
        return if (showNsfw) {
            preferences.getStringSet(PREF_EXCLUDE_GENRE_ADULT, emptySet())!!
        } else {
            preferences.getStringSet(PREF_EXCLUDE_GENRE, emptySet())!!
        }
    }

    private fun typePref(): String = preferences.getString(PREF_TYPE_KEY, "")!!
    private fun statusPref(): String = preferences.getString(PREF_STATUS_KEY, "")!!

    // =============================== Preferences =================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val showNsfw = preferences.getBoolean(PREF_NSFW_KEY, false)

        val genreNames = genreList.map { it.first }.toTypedArray()
        val genreIds = genreList.map { it.second }.toTypedArray()

        val nsfwPref = SwitchPreferenceCompat(screen.context).apply {
            key = PREF_NSFW_KEY
            title = "Show NSFW / 18+ Content"
            summary = "Shows manga marked as 18+. Otherwise, they are hidden from lists."
            setDefaultValue(false)
        }
        screen.addPreference(nsfwPref)

        val typePref = ListPreference(screen.context).apply {
            key = PREF_TYPE_KEY
            title = "Type Filter"
            entries = arrayOf("All", "Manga", "Manhwa", "Manhua", "Novel", "One-Shot", "Doujinshi")
            entryValues = arrayOf("", "MANGA", "MANHWA", "MANHUA", "NOVEL", "ONE-SHOT", "DOUJINSHI")
            setDefaultValue("")
            summary = "Applies to Popular & Latest"
        }
        screen.addPreference(typePref)

        val statusPref = ListPreference(screen.context).apply {
            key = PREF_STATUS_KEY
            title = "Status Filter"
            entries = arrayOf("All", "Ongoing", "Completed", "Hiatus", "Releasing")
            entryValues = arrayOf("", "ongoing", "completed", "hiatus", "releasing")
            setDefaultValue("")
            summary = "Applies to Popular & Latest"
        }
        screen.addPreference(statusPref)

        val normalGenrePref = MultiSelectListPreference(screen.context).apply {
            key = PREF_EXCLUDE_GENRE
            title = "Genre Blacklist"
            summary = "Exclude genres when browsing without 18+ content."
            entries = genreNames
            entryValues = genreIds
            setDefaultValue(emptySet<String>())
            setEnabled(!showNsfw)
        }
        screen.addPreference(normalGenrePref)

        val adultGenrePref = MultiSelectListPreference(screen.context).apply {
            key = PREF_EXCLUDE_GENRE_ADULT
            title = "Genre Blacklist (Adult)"
            summary = "Exclude genres when browsing with 18+ content."
            entries = genreNames
            entryValues = genreIds
            setDefaultValue(emptySet<String>())
            setEnabled(showNsfw)
        }
        screen.addPreference(adultGenrePref)

        nsfwPref.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            normalGenrePref.setEnabled(!enabled)
            adultGenrePref.setEnabled(enabled)
            true
        }

        val rateLimitPref = ListPreference(screen.context).apply {
            key = PREF_RATE_LIMIT_KEY
            title = "Image Requests Limit"
            entries = arrayOf("1 image per 1.50 seconds", "1 image per 1.75 seconds", "1 image per 2.00 seconds", "1 image per 2.25 seconds", "1 image per 2.50 seconds")
            entryValues = arrayOf("1500", "1750", "2000", "2250", "2500")
            setDefaultValue("2000")
            summary = "%s\nWarning: Lowering this might cause 429 errors."
        }
        screen.addPreference(rateLimitPref)
    }

    companion object {
        private const val PREF_NSFW_KEY = "pref_nsfw"
        private const val PREF_TYPE_KEY = "pref_type"
        private const val PREF_STATUS_KEY = "pref_status"
        private const val PREF_EXCLUDE_GENRE = "pref_exclude_genre"
        private const val PREF_EXCLUDE_GENRE_ADULT = "pref_exclude_genre_adult"
        private const val PREF_RATE_LIMIT_KEY = "pref_rate_limit"

        private val READER_TOKEN_REGEX = Regex("""readerToken["']?\s*:\s*["']([^"']+)["']""")
        private val PAGE_ORDER_REGEX = Regex("""["']?order["']?\s*:\s*(\d+)""")
        private val CHAPTER_NUMBER_REGEX = Regex("""Chapter\s+([\d.]+)""")
        private val RELATIVE_DATE_REGEX = Regex("(\\d+)\\s+(minute|hour|day|week|month|year)s?\\s+ago")
        private val ORIGIN_REGEX = Regex("(Japanese|Korean|Chinese|English)", RegexOption.IGNORE_CASE)
        private val YEAR_REGEX = Regex("^\\d{4}$")
        private val RATING_REGEX = Regex("(\\d)\\.0(?=[/ ])")
        private val INTERPUNCT_REGEX = Regex("\\s*·\\s*")
    }
}
