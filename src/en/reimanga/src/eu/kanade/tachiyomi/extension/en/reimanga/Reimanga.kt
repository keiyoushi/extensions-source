package eu.kanade.tachiyomi.extension.en.reimanga

import android.util.Base64
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getLongOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class Reimanga :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("showAdultContent" to "true")
        protocols(listOf(Protocol.HTTP_1_1))
    }

    private val preferences by getPreferencesLazy()

    private val rscHeaders
        get() = headersBuilder()
            .set("rsc", "1")
            .build()

    private val spaceRegex = Regex("""\s+""")

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) {
            val filters = getFilterList().apply {
                firstInstance<SortFilter>().state = Filter.Sort.Selection(2, false)
            }
            return getSearchMangaList(page - 1, "", filters)
        }

        val data = client.get("$baseUrl/api/manga/trending?limit=100", Fetch.XHR).parseAs<List<Manga>>()
        return MangasPage(data.map { it.toSManga(baseUrl) }, true)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            }
            val excluded = mutableListOf<String>()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.sort)
                        addQueryParameter("order", filter.direction)
                    }
                    is StatusFilter -> {
                        filter.status?.also { status ->
                            addQueryParameter("status", status)
                        }
                    }
                    is GenreFilter -> {
                        val included = filter.included
                        if (included.isNotEmpty()) {
                            addQueryParameter("genre", included.joinToString(","))
                        }
                        excluded.addAll(filter.excluded)
                    }
                    is TagFilter -> {
                        val included = filter.included
                        if (included.isNotEmpty()) {
                            addQueryParameter("tag", included.joinToString(","))
                        }
                        excluded.addAll(filter.excluded)
                    }
                    else -> {}
                }
            }
            if (excluded.isNotEmpty()) {
                addQueryParameter("excludeGenres", excluded.joinToString(","))
            }
        }.build()

        val data = client.get(url, Fetch.XHR).parseAs<MangaList>()
        return MangasPage(data.data.map { it.toSManga(baseUrl) }, data.pagination.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return getMangaUpdate(
            SManga.create().apply { this.url = slug },
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val requestId = manga.memo.getLongOrNull(MANGA_ID_MEMO)
            ?: manga.url.substringAfterLast("-").toLong()

        var apiManga = client.get("$baseUrl/api/manga/$requestId", Fetch.XHR)
            .parseAs<MangaPage>()
            .manga

        // DMCA / duplicate entries point at a main series with real metadata & chapters
        if (apiManga.resolvedId != requestId) {
            apiManga = client.get("$baseUrl/api/manga/${apiManga.resolvedId}", Fetch.XHR)
                .parseAs<MangaPage>()
                .manga
        }

        val updatedManga = if (fetchDetails) {
            apiManga.toSManga(baseUrl)
        } else {
            manga.apply {
                memo = buildJsonObject { put(MANGA_ID_MEMO, apiManga.resolvedId) }
            }
        }

        val updatedChapters = if (fetchChapters) {
            parseChapterList(client.get(apiManga.chapterPageUrl(baseUrl), rscHeaders, Fetch.XHR).extractNextJs())
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseChapterList(data: ChapterList?): List<SChapter> {
        if (data == null) return emptyList()

        return data.chapters.map { chapter ->
            SChapter.create().apply {
                url = "${data.manga.slug}-${data.manga.id}/${chapter.id}"
                name = chapter.name.replace(spaceRegex, " ").trim()
                date_upload = Instant.tryParse(
                    chapter.uploadDate ?: chapter.updatedAt ?: chapter.createdAt,
                )
            }
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaId = manga.url.substringAfterLast("-")
        return client.get("$baseUrl/api/manga/$mangaId/similar", Fetch.XHR)
            .parseAs<List<Manga>>()
            .map { it.toSManga(baseUrl) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val handoff = client.get(chapterUrl, rscHeaders, Fetch.XHR).extractNextJs<ChapterHandoff>()
            ?: return emptyList()
        val fetch = Fetch.XHR.from(chapterUrl)

        val tokenUrl = "$baseUrl/api/reader/chapter-token".toHttpUrl()
        val tokenBody = ChapterTokenRequest(handoff.chapter.chapterId, handoff.readerNonce, handoff.chapter.mangaId)
        val token = signedCall(handoff, chapterUrl, "POST", tokenUrl) { headers ->
            client.post(tokenUrl, headers.build(), fetch, tokenBody.toJsonRequestBody(), ensureSuccess = false)
        }.parseAs<ChapterToken>().data.token

        val imagesUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/chapters/${handoff.chapter.chapterId}/images")
            .addQueryParameter("server", "1")
            .build()
        val data = signedCall(handoff, chapterUrl, "GET", imagesUrl) { headers ->
            val imagesHeaders = headers.set("X-Chapter-Token", token).build()
            client.get(imagesUrl, imagesHeaders, fetch, CacheControl.FORCE_NETWORK, ensureSuccess = false)
        }.parseAs<Images>()

        return data.imageUrls().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    /**
     * A call signed like the site's reader, resent after a fresh key and after the Turnstile check as the reader does.
     * [send] returns non-2xx responses (`ensureSuccess = false`).
     */
    private suspend fun signedCall(
        handoff: ChapterHandoff,
        chapterUrl: String,
        method: String,
        url: HttpUrl,
        send: suspend (Headers.Builder) -> Response,
    ): Response {
        var session = getReaderSession(handoff, chapterUrl)
        val retried = mutableSetOf<Boolean>()
        while (true) {
            val response = send(session.signedHeaders(method, url))
            if (response.isSuccessful) return response

            val refusal = response
                .takeIf { it.code == 403 && it.header("Content-Type")?.startsWith("application/json") == true }
                ?.parseAs<GuardRefusal>()
            response.close()
            val challenge = refusal?.error == "challenge_required"
            if (refusal?.guard != true || !retried.add(challenge)) throw HttpException(response.code)

            session = getReaderSession(handoff, chapterUrl, rejected = session, challenge = challenge)
        }
    }

    private val readerSessionMutex = Mutex()
    private var readerSession: ReaderSession? = null

    /** The reader's guard key and Turnstile pass, kept until they expire like the site's copies. */
    private suspend fun getReaderSession(
        handoff: ChapterHandoff,
        chapterUrl: String,
        rejected: ReaderSession? = null,
        challenge: Boolean = false,
    ): ReaderSession = readerSessionMutex.withLock {
        val now = Clock.System.now()
        val current = readerSession?.takeIf { now < it.expiresAt - 1.minutes }
        current?.takeIf { it !== rejected && (!challenge || it.hasPass(now)) }?.let { return it }

        val session = if (challenge && current != null) {
            val siteKey = current.turnstileSiteKey ?: throw IOException("Reader check required but no site key given")
            val turnstileToken = getTurnstileToken(chapterUrl, siteKey)
            val pass = client.post(
                "$baseUrl/api/reader/guard/verify",
                Fetch.XHR.from(chapterUrl),
                GuardVerifyRequest(turnstileToken).toJsonRequestBody(),
            ).parseAs<ReaderPass>().data
            current.copy(pass = pass.pass, passExpiresAt = now + pass.expiresIn.seconds)
        } else {
            val guard = client.post(
                "$baseUrl/api/reader/guard",
                Fetch.XHR.from(chapterUrl),
                GuardRequest(handoff.readerNonce, handoff.chapter.mangaId).toJsonRequestBody(),
            ).parseAs<ReaderGuard>().data

            ReaderSession(
                kid = guard.kid,
                key = guard.key,
                clockOffset = guard.now - now.epochSeconds,
                expiresAt = now + guard.expiresIn.seconds,
                turnstileSiteKey = guard.turnstileSiteKey,
                pass = readerSession?.pass,
                passExpiresAt = readerSession?.passExpiresAt,
            )
        }
        session.also { readerSession = it }
    }

    private fun ReaderSession.signedHeaders(method: String, url: HttpUrl): Headers.Builder {
        val now = Clock.System.now()
        val ts = now.epochSeconds + clockOffset
        val mac = "v1\n$method\n${url.encodedPath}\n$ts\n-"
            .hmacSha256(Base64.decode(key, Base64.URL_SAFE))
            .copyOf(16)
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

        return headersBuilder()
            .set("X-Reader-Sig", "v1.$kid.$ts.-.$mac")
            .apply {
                if (hasPass(now)) set("X-Reader-Pass", pass!!)
            }
    }

    override fun getImageRequest(page: Page): Request {
        val request = super.getImageRequest(page)
        val range = request.url.fragment ?: return request

        return request.newBuilder()
            .header("Range", "bytes=$range")
            .build()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val tagList = client.get("$baseUrl/advanced-search", rscHeaders, Fetch.XHR)
            .extractNextJs<TagList>()
            ?: throw IOException("Failed to extract tags")
        return tagList.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            StatusFilter(),
        )

        val cachedTags = data?.parseAs<TagList>()
        if (cachedTags != null) {
            val excluded = preferences.getStringSet(EXCLUDE_TAG_PREF, emptySet()) ?: emptySet()
            val genres = cachedTags.genres.map { tag ->
                TriStateOption(
                    tag.name,
                    tag.slug,
                    if (tag.slug in excluded) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE,
                )
            }.sortedBy { it.name }
            val tags = cachedTags.tags.map { tag ->
                TriStateOption(
                    tag.name,
                    tag.slug,
                    if (tag.slug in excluded) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE,
                )
            }.sortedBy { it.name }

            filters.add(GenreFilter(genres))
            filters.add(TagFilter(tags))
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val filters = getFilterList()
        val tags = buildList {
            filters.firstInstanceOrNull<GenreFilter>()?.state?.let(::addAll)
            filters.firstInstanceOrNull<TagFilter>()?.state?.let(::addAll)
        }.sortedBy { it.name }

        MultiSelectListPreference(screen.context).apply {
            key = EXCLUDE_TAG_PREF
            title = "Exclude Tags from Browse"
            entries = tags.map { it.name }.toTypedArray()
            entryValues = tags.map { it.value }.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(tags.isNotEmpty())

            fun updateSummary(pref: MultiSelectListPreference, selected: Set<String>?) {
                pref.summary = if (selected.isNullOrEmpty()) {
                    "None"
                } else {
                    val entryMap = pref.entryValues.zip(pref.entries).toMap()
                    selected.joinToString { entryMap[it] ?: it }
                }
            }

            updateSummary(this, preferences.getStringSet(EXCLUDE_TAG_PREF, emptySet()))

            setOnPreferenceChangeListener { pref, newValue ->
                val updated = pref as MultiSelectListPreference

                @Suppress("UNCHECKED_CAST")
                val newSet = newValue as Set<String>

                updateSummary(updated, newSet)
                true
            }
        }.also(screen::addPreference)
    }
}

private data class ReaderSession(
    val kid: String,
    val key: String,
    val clockOffset: Long,
    val expiresAt: Instant,
    val turnstileSiteKey: String?,
    val pass: String?,
    val passExpiresAt: Instant?,
) {
    fun hasPass(now: Instant) = pass != null && passExpiresAt != null && now < passExpiresAt - 1.minutes
}

const val EXCLUDE_TAG_PREF = "pref_exclude_tag"
private const val MANGA_ID_MEMO = "mangaId"
