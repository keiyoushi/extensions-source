package eu.kanade.tachiyomi.extension.fr.ono

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale

class Ono :
    HttpSource(),
    ConfigurableSource {

    override val name = "Ono"
    override val baseUrl = "https://www.ono.live"
    override val lang = "fr"
    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .addInterceptor(::authInterceptor)
        .addInterceptor(::wafInterceptor)
        .addInterceptor(::imageRetryInterceptor)
        .build()

    private val apiUrl = "https://ws.ono.live/graphql"

    // Cognito stores its JWTs as cookies on the www domain. The website sends the
    // idToken as `Authorization: bearer <jwt>` to the GraphQL API. Mirror that:
    // pull the idToken cookie (set after WebView login) and attach it to API calls.
    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != API_HOST || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val token = bearerToken()
        val newRequest = if (token != null) {
            request.newBuilder().header("Authorization", "bearer $token").build()
        } else {
            request
        }
        return chain.proceed(newRequest)
    }

    private fun bearerToken(): String? {
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        val prefix = "CognitoIdentityServiceProvider.$COGNITO_CLIENT_ID"
        val sub = cookies.firstOrNull { it.name == "$prefix.LastAuthUser" }?.value
            ?: return null
        val token = cookies.firstOrNull { it.name == "$prefix.$sub.idToken" }?.value
            ?: return null
        return token.takeUnless { isJwtExpired(it) }
    }

    private fun isJwtExpired(jwt: String): Boolean = try {
        val payload = jwt.split('.')[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
        val exp = Regex("\"exp\":(\\d+)").find(json)!!.groupValues[1].toLong()
        // Treat as expired slightly early to avoid mid-request expiry.
        System.currentTimeMillis() / 1000 >= exp - 30
    } catch (_: Exception) {
        false
    }

    private fun wafInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 202 && response.header("x-amzn-waf-action") == "challenge") {
            response.close()
            throw IOException(
                "AWS WAF challenge déclenché. Ouvrez le site dans la WebView " +
                    "pour résoudre le défi de sécurité, puis réessayez.",
            )
        }
        return response
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val rscHeaders by lazy { headersBuilder().add("RSC", "1").build() }

    private val gqlHeaders by lazy {
        headersBuilder()
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .add("age-confirmed", "true")
            .add("ono-platform", "website")
            .add("ono-product", "FR")
            .build()
    }

    private fun contentPath(contentType: String): String = if (contentType.equals("MANGA", ignoreCase = true)) "manga" else "webtoon"

    // =============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = graphQLPost(apiUrl, gqlHeaders, query = RANKING_QUERY, operationName = "getCatalogRanking")

    override fun popularMangaParse(response: Response): MangasPage {
        val series = response.parseGraphQLAs<RankingData>()
            .getCatalogRanking?.series!!
        val mangas = series.map { s ->
            SManga.create().apply {
                title = s.title
                thumbnail_url = s.imageURL
                setUrlWithoutDomain("/${contentPath(s.contentType)}/${s.slug}")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = graphQLPost(
        apiUrl,
        gqlHeaders,
        query = SEARCH_QUERY,
        operationName = "searchCatalogByTerm",
        variables = buildJsonObject { put("term", query) },
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val series = response.parseGraphQLAs<SearchCatalogData>()
            .searchCatalogByTerm?.series!!
        val mangas = series.map { s ->
            SManga.create().apply {
                title = s.title
                thumbnail_url = "https://catalog.ono.live/master/contents/${s.id}/thumbnail"
                setUrlWithoutDomain("/${contentPath(s.contentType)}/${s.slug}")
            }
        }
        return MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    private fun seriesDetail(response: Response): SeriesDetail {
        response.extractNextJs<SeriesDetail>()?.let { return it }

        // RSC payload can be partial on client-side navigation; retry cache-busted.
        val retryUrl = response.request.url.newBuilder()
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val retry = client.newCall(
            response.request.newBuilder().url(retryUrl)
                .cacheControl(CacheControl.FORCE_NETWORK).build(),
        ).execute()
        return retry.extractNextJs<SeriesDetail>()!!
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = seriesDetail(response)
        return SManga.create().apply {
            title = series.title
            thumbnail_url = series.cover
            author = series.contributors.joinToString { it.name }.ifBlank { null }
            artist = author
            description = listOfNotNull(
                series.punchline?.trim()?.takeIf { it.isNotEmpty() },
                series.summary?.trim()?.takeIf { it.isNotEmpty() },
            ).joinToString("\n\n").ifBlank { null }
            genre = (series.genres.map { it.label } + series.tags.map { it.label })
                .mapNotNull { it.trim().takeIf { g -> g.isNotEmpty() } }
                .joinToString { it.replaceFirstChar { c -> c.titlecase(Locale.FRENCH) } }
            status = parseStatus(series.publicationStatus)
            setUrlWithoutDomain("/${contentPath(series.contentType)}/${series.slug}")
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.uppercase()) {
        "ONGOING" -> SManga.ONGOING
        "FINISHED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "UNPUBLISHED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = seriesDetail(response)
        val path = contentPath(series.contentType)
        val showPremium = preferences.getBoolean(SHOW_PREMIUM_KEY, SHOW_PREMIUM_DEFAULT)
        val showWaf = preferences.getBoolean(SHOW_WAF_KEY, SHOW_WAF_DEFAULT)

        return series.seriesElements
            .mapNotNull { el ->
                val locked = el.price != null && el.price != "0" && el.isBought != true
                // Any non-null waitAndRead (WaitAndReadAvailable / InUse / ...) = wait-until-free eligible.
                val isWaf = locked && el.waitAndRead != null
                val isPremium = locked && !isWaf

                if (isWaf && !showWaf) return@mapNotNull null
                if (isPremium && !showPremium) return@mapNotNull null

                SChapter.create().apply {
                    val label = el.title?.trim()?.takeIf { it.isNotBlank() } ?: "Episode ${el.num}"
                    name = when {
                        isWaf -> "🕐 $label"
                        isPremium -> "🔒 $label"
                        else -> label
                    }
                    chapter_number = el.num.toFloatOrNull() ?: -1f
                    setUrlWithoutDomain("/$path/${series.slug}/${el.num}")
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ================================

    @Volatile private var lastSlug = ""

    @Volatile private var lastNum = ""

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.trim('/').split('/')
        lastNum = segments.last()
        lastSlug = segments[segments.size - 2]
        return startReadingRequest(lastNum, lastSlug)
    }

    private fun startReadingRequest(num: String, slug: String): Request = graphQLPost(
        apiUrl,
        gqlHeaders,
        query = START_READING_QUERY,
        operationName = "StartReadingSession",
        variables = buildJsonObject {
            put("num", num)
            put("slug", slug)
        },
    )

    override fun pageListParse(response: Response): List<Page> {
        val payload = fetchReadingSession(lastNum, lastSlug)

        when (payload.__typename) {
            "SessionStarted" -> {}
            "PublicationUnavailable" ->
                throw Exception("Chapitre indisponible: ${payload.unavailabilityReason ?: "inconnu"}")
            "UserHasNotAccess" -> {
                val methods = payload.publicationAccessMethods
                if (methods.any { it.__typename == "NotLoggedIn" }) {
                    throw Exception(
                        "Connexion requise. Connectez-vous au site via la WebView " +
                            "pour lire les chapitres 🕐/🔒.",
                    )
                }
                val used = methods
                    .firstOrNull { it.__typename == "WaitNReadIsUsed" }
                if (used?.waitAndReadReloadDelay != null) {
                    val h = used.waitAndReadReloadDelay / 3600
                    val m = (used.waitAndReadReloadDelay % 3600) / 60
                    throw Exception(
                        "Créneau 'wait until free' épuisé. Prochain déblocage gratuit dans " +
                            "~${h}h${m.toString().padStart(2, '0')}.",
                    )
                }
                throw Exception(
                    "Chapitre premium (coins/ticket requis). Débloquez-le sur le site via la WebView.",
                )
            }
            else -> throw Exception("Accès refusé (${payload.__typename})")
        }

        val pages = payload.publicationMetadata?.pages!!
        val fragment = "$lastSlug/$lastNum"
        return pages.mapIndexed { i, url -> Page(i, imageUrl = "$url#$fragment") }
    }

    private fun fetchReadingSession(num: String, slug: String): ReadingSessionPayload {
        var payload = client.newCall(startReadingRequest(num, slug)).execute()
            .parseGraphQLAs<StartReadingSessionData>()
            .startReadingSessionBySlugAndNum!!

        if (payload.__typename == "UserHasNotAccess") {
            val wnr = payload.publicationAccessMethods
                .firstOrNull { it.__typename == "WaitNReadAvailable" && it.publicationId != null }
            if (wnr?.publicationId != null) {
                unlockByWaitAndRead(wnr.publicationId)
                payload = client.newCall(startReadingRequest(num, slug)).execute()
                    .parseGraphQLAs<StartReadingSessionData>()
                    .startReadingSessionBySlugAndNum!!
            }
        }

        return payload
    }

    private fun unlockByWaitAndRead(publicationId: String) {
        val request = graphQLPost(
            apiUrl,
            gqlHeaders,
            query = UNLOCK_WNR_MUTATION,
            operationName = "unlockPublicationByWnR",
            variables = buildJsonObject { put("publicationId", publicationId) },
        )

        val result = client.newCall(request).execute()
            .parseGraphQLAs<UnlockData>()
            .unlockPublicationByWnR!!
        if (result.success != true) {
            throw Exception(
                "Échec du déblocage 'wait until free'" +
                    (result.code?.let { " ($it)" } ?: "") + ".",
            )
        }
    }

    // CloudFront signed URLs expire. If a chapter is preloaded and read later,
    // images return 403. Re-fetch the reading session to get fresh signed URLs.
    // Chapter slug/num is embedded as a URL fragment (never sent to server).
    private fun imageRetryInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != HttpURLConnection.HTTP_FORBIDDEN) return response

        val fragment = chain.request().url.fragment ?: return response
        val parts = fragment.split('/')
        if (parts.size != 2) return response
        val (slug, num) = parts

        response.close()

        val payload = fetchReadingSession(num, slug)
        val freshPages = payload.publicationMetadata?.pages
            ?: throw IOException("Pas de pages dans la session rafraîchie")

        val originalPath = chain.request().url.encodedPath
        val freshUrl = freshPages.firstOrNull { it.toHttpUrl().encodedPath == originalPath }
            ?: throw IOException("Page introuvable après rafraîchissement")

        return chain.proceed(
            chain.request().newBuilder()
                .url(freshUrl)
                .build(),
        )
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = SHOW_PREMIUM_KEY
            title = "Afficher les chapitres premium"
            summary = "Afficher les chapitres payants (identifiés par 🔒) dans la liste."
            setDefaultValue(SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)

        CheckBoxPreference(screen.context).apply {
            key = SHOW_WAF_KEY
            title = "Afficher les chapitres 'Wait until free'"
            summary = "Afficher les chapitres lisibles via un créneau d'attente (identifiés par 🕐)."
            setDefaultValue(SHOW_WAF_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val API_HOST = "ws.ono.live"
        private const val COGNITO_CLIENT_ID = "12kanvg0bocd5hjtuul46phv7s"

        private val SEARCH_QUERY =
            $$"query searchCatalogByTerm($term:String!)" +
                $$"{searchCatalogByTerm(input:{term:$term})" +
                "{series{id title contentType slug}}}"

        private val RANKING_QUERY =
            $$"query getCatalogRanking($genreSlug:String)" +
                $$"{getCatalogRanking(filter:{genreSlug:$genreSlug})" +
                "{__typename ...on GetCatalogRankingPayload{" +
                "series{id slug title contentType imageURL}}" +
                "...on ErrorWithCode{__typename code}}}"
        private const val SHOW_PREMIUM_KEY = "show_premium_chapters"
        private const val SHOW_PREMIUM_DEFAULT = false
        private const val SHOW_WAF_KEY = "show_wait_until_free_chapters"
        private const val SHOW_WAF_DEFAULT = true

        private val START_READING_QUERY =
            $$"query StartReadingSession($num:String!$slug:String!)" +
                $$"{startReadingSessionBySlugAndNum(input:{num:$num slug:$slug})" +
                "{...C}}" +
                "fragment C on StartReadingSessionPayload{" +
                "...on PublicationUnavailable{__typename unavailabilityReason}" +
                "...on UserHasNotAccess{__typename publicationAccessMethods{...A}}" +
                "...on ErrorWithCode{__typename code}" +
                "...on SessionStarted{__typename publicationMetadata{pages}}}" +
                "fragment A on PublicationAccessMethod{__typename " +
                "...on WaitNReadIsUsed{waitAndReadReloadDelay}" +
                "...on WaitNReadAvailable{publicationId}" +
                "...on CanBeBought{publicationId}" +
                "...on NotEnoughCoins{publicationId}" +
                "...on GiftTicketsAvailable{publicationId}}"

        private val UNLOCK_WNR_MUTATION =
            $$"mutation unlockPublicationByWnR($publicationId:UUID!)" +
                $$"{unlockPublicationByWnR(input:{publicationId:$publicationId})" +
                "{...on UnlockPublicationResult{success}" +
                "...on ErrorWithCode{__typename code}}}"
    }
}
